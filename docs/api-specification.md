# B2B 편의점 본사-가맹점 발주 관리 시스템 API 명세서

버전: v2.2 (완료·반려 후 동일 상품 재발주 지원)

이 문서는 v1.0 Draft(설계 초안) 이후 실제로 구현/배포된 코드를 기준으로 다시 작성했다. 서비스 이름, 경로, 요청/응답 필드는 모두 현재 저장소(`skaka-franchise-order-system`) 코드와 일치한다. 초안과 달라진 부분은 각 절에 표시했다.

## 0. v1.0 초안과 달라진 점 요약

| 항목 | v1.0 초안 | 실제 구현(v2.2) |
|---|---|---|
| 서비스 이름 | User/Product/Order/Inventory Service | `user-service`, `course-service`(Product), `enrollment-service`(Order), `payment-service`, `recommend-service` |
| 외부 경로 | `/api/products`, `/api/orders`, `/api/admin/orders`, `/api/inventories` | `/api/courses`, `/api/enrollments`, `/api/enrollments/admin`, 별도 재고 API 없음(`courses.enrollmentCount` 필드로 대체) |
| 상태 흐름 | `REQUESTED → APPROVED → SHIPPING → DELIVERED → RECEIVED` | `REQUESTED → APPROVED → RECEIVED` (`SHIPPING`/`DELIVERED` 없음) |
| 발주 단위 | 여러 상품을 담는 장바구니형 `items[]` | 상품 1개당 발주 1건 (`courseId` 단건)이며 `quantity` 1~999 지원 |
| 승인/반려/입고 HTTP 메서드 | `PATCH` | `POST` (API Gateway가 `Origin` 헤더가 실린 `PATCH`를 항상 403 처리하는 버그가 있어 우회) |
| 상품 수정 메서드 | `PATCH` | `PUT` (같은 이유로 `PUT` 사용, `PATCH`도 하위 호환으로 같이 받음) |
| 결제/정산 | "B2B MVP에서는 제외" | `payment-service`로 실제 구현됨. 발주 **승인 시점**에 자동 정산 처리 |
| 추천 | "B2B MVP 범위에서 제외" | `recommend-service`로 실제 구현됨 (Gemini 연동, 키 없으면 규칙 기반으로 폴백) |
| 반려 사유 | `rejectionReason` 필드로 응답에 포함 | 실제로 DB 저장 + 응답 필드로 노출됨 (`rejectReason`) |
| 목록 응답 형식 | `data.items` + 페이지네이션 | `data`에 배열을 바로 담음 (페이지네이션 없음) |

## 1. 시스템 개요

### 1.1 핵심 사용자 / 역할

역할은 두 단계로 나뉜다.

1. **DB/JWT 원본 값** — `user-service`의 `User.Role` enum과 `auth-server`가 발급하는 JWT의 `role` 클레임이 실제로 쓰는 값. API Gateway는 이 값을 그대로 `X-User-Role` 헤더에 담아 하위 서비스로 전달한다.
   - `STUDENT` = 가맹점 관리자
   - `INSTRUCTOR` = 본사 관리자
2. **화면 표기용 별칭(alias)** — 프론트엔드가 사람이 읽기 좋은 이름으로 보여줄 때만 쓰는 이름. DB나 JWT에는 절대 저장되지 않는다 (`vue-frontend/src/utils/business.js`의 `roleAliases`).
   - `STORE_ADMIN` ↔ `STUDENT`
   - `HEADQUARTERS_ADMIN` ↔ `INSTRUCTOR`

> 원래 `auth-server`, `api-gateway`, `eureka-server`는 이 프로젝트에서 소스가 제공되지 않는 프리빌트 이미지라 값 이름을 바꿀 수 없다. 그래서 회원가입 시 프론트가 `STORE_ADMIN`/`HEADQUARTERS_ADMIN`을 입력받아도, 서버로 보낼 때는 `backendRole()` 함수로 `STUDENT`/`INSTRUCTOR`로 변환한다. **각 백엔드 컨트롤러의 `X-User-Role` 권한 체크는 항상 `STUDENT`/`INSTRUCTOR` 원본 값과 비교한다.**

| 역할(원본) | 별칭 | 설명 |
|---|---|---|
| `INSTRUCTOR` | `HEADQUARTERS_ADMIN` | 상품 관리, 발주 승인/반려, 전체 정산 조회 |
| `STUDENT` | `STORE_ADMIN` | 상품 조회, 발주 생성, 본인 발주 상태 조회, 입고 확인, 본인 정산 조회 |

### 1.2 핵심 상태 흐름

```text
REQUESTED -> APPROVED -> RECEIVED
     |
     +------> REJECTED
```

| 현재 상태 | 허용되는 다음 상태 | 변경 주체 | 실제 엔드포인트 |
|---|---|---|---|
| `REQUESTED` | `APPROVED` | 본사 관리자 | `POST /api/enrollments/admin/{id}/approve` |
| `REQUESTED` | `REJECTED` | 본사 관리자 | `POST /api/enrollments/admin/{id}/reject` |
| `APPROVED` | `RECEIVED` | 가맹점 관리자 | `POST /api/enrollments/{id}/receive` |
| `RECEIVED` | 없음 | - | - |
| `REJECTED` | 없음 | - | - |

허용되지 않은 상태 전이는 `409 Conflict`로 응답한다. 입고(재고 반영)는 각 발주의 `APPROVED` 상태에서 한 번만 가능하고, 이미 `RECEIVED`인 발주는 다시 처리되지 않는다. 완료된 발주는 이력으로 남으며 같은 상품을 새로 발주할 수 있다.

**본사가 발주를 승인하는 순간 정산(결제)이 자동으로 함께 처리된다.** 자세한 내용은 7절 참고.

## 2. MSA 및 라우팅

### 2.1 외부 요청 흐름

```text
Client
  -> Auth Server(9000) : OAuth2 Authorization Code 로그인
  -> API Gateway(8080) : Bearer Access Token 전달
  -> 각 서비스 : Gateway가 JWT 검증 후 X-User-Id/X-User-Role 헤더를 붙여 라우팅
```

모든 외부 API 요청은 API Gateway(8080)를 통과한다. `auth-server`/`api-gateway`/`eureka-server`는 소스가 없는 프리빌트 이미지라 **새 Gateway 라우트를 추가하거나 경로를 바꿀 수 없다.** 그래서 신규 기능은 기존에 이미 뚫려 있는 서비스 경로(`/api/courses`, `/api/enrollments`, `/api/payments`, `/api/recommend`, `/api/users`) 아래에 하위 경로로만 추가한다.

| 구성 요소 | 컨테이너명 | 포트 | 책임 |
|---|---|---:|---|
| Eureka Server | `lecture-eureka` | 8761 | 서비스 등록 및 탐색 |
| Auth Server | `lecture-auth` | 9000 | OAuth2 로그인, Authorization Code, JWT 발급 |
| API Gateway | `lecture-gateway` | 8080 | 단일 진입점, 라우팅, JWT 검증, 사용자 헤더 전달 |
| User Service | `lecture-user` | 8081 | 사용자와 역할 관리 |
| Course(Product) Service | `lecture-course` | 8082 | 상품 등록/수정/조회, 가맹점 재고(`enrollmentCount`) |
| Enrollment(Order) Service | `lecture-enrollment` | 8083 | 발주 생성 및 상태 전이 |
| Payment Service | `lecture-payment` | 8084 | 발주 승인 시 정산(결제) 처리, 정산 내역 조회 |
| Recommend Service | `lecture-recommend` | 8085 | 가맹점별 발주 추천 (FastAPI, Gemini 연동) |
| MariaDB | `lecturedb` | 3379(local) / 3306(container) | 서비스별 업무 데이터 저장 (모든 서비스가 `lecture_db` 하나를 공유) |
| Kafka | `lecture-kafka` | 9092 | `payment.completed` 등 비동기 이벤트 |

### 2.2 Gateway 라우팅 (실제)

| 외부 경로 | 대상 서비스 |
|---|---|
| `/api/users/**` | User Service |
| `/api/courses/**` | Course Service (구 Product) |
| `/api/enrollments/**` | Enrollment Service (구 Order, `/api/enrollments/admin/**` 포함) |
| `/api/payments/**` | Payment Service |
| `/api/recommend/**` | Recommend Service |
| `/oauth2/**`, `/login`, `/logout`, `/userinfo` | Auth Server |

`/api/courses/internal/**`, `/api/enrollments/internal/**`, `/api/payments/internal/request`는 Gateway를 통해 외부에서도 호출은 가능하지만(라우트 자체를 세분화할 수 없음), 실제로는 서비스 간 내부 호출(WebClient, 컨테이너 네트워크 `http://course-service:8082` 등)로만 사용하고 클라이언트가 직접 부르지 않는다. 이 내부 API들은 `X-User-*` 헤더 검증을 하지 않으므로 별도 인증 계층(Client Credentials 등)을 아직 두지 않았다는 점은 알려진 제약이다 (12.3절 참고).

## 3. 공통 규칙

### 3.1 기본 URL

```text
외부 API: http://localhost:8080
Content-Type: application/json
문자 인코딩: UTF-8
```

### 3.2 인증 헤더

```http
Authorization: Bearer {access_token}
```

Gateway는 JWT 검증 후 다음 헤더를 하위 서비스에 전달한다.

```http
X-User-Id: 4
X-User-Role: STUDENT
```

`X-User-Email`은 현재 Gateway가 전달하지 않는다 (문서 v1.0에는 있었으나 실제로는 미구현). 각 서비스는 이 두 헤더만으로 인가를 판단하며, `SecurityConfig`는 `anyRequest().permitAll()`로 열어두고 **컨트롤러 코드에서 직접 `X-User-Role` 값을 비교**하는 방식을 전 서비스가 동일하게 사용한다 (JWT를 서비스별로 다시 검증하지 않음).

### 3.3 OAuth2 엔드포인트

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/oauth2/authorize` | 로그인 및 인증 코드 요청 |
| `POST` | `/oauth2/token` | Authorization Code를 Access Token으로 교환 |
| `GET` | `/oauth2/jwks` | Resource Server가 JWT 검증에 사용하는 공개키 |

```text
client_id: web-client
client_secret: web-secret
grant_type: authorization_code
scope: openid profile read write
redirect_uri: http://localhost:3000/callback
```

### 3.4 성공 응답

모든 서비스가 동일한 래퍼를 쓴다 (`XxxDto.ApiResponse`). **v1.0에 있던 `data.items` + 페이지네이션 포맷은 실제로 사용하지 않는다.** 목록도 `data`에 배열을 그대로 담는다.

```json
{
  "success": true,
  "message": "성공",
  "data": {}
}
```

```json
{
  "success": true,
  "message": "성공",
  "data": [ ]
}
```

### 3.5 오류 응답

```json
{
  "success": false,
  "message": "본인 소유 정산 내역만 조회할 수 있습니다",
  "data": null
}
```

| HTTP 상태 | 의미 | 예외 타입 | 대표 상황 |
|---:|---|---|---|
| `400` | 요청 형식 또는 필드 검증 실패 | `IllegalArgumentException`, `MethodArgumentNotValidException` | 필수값 누락, 존재하지 않는 상품/발주 ID |
| `401` | 인증 실패 | (Gateway 단계) | Access Token 누락 또는 만료 |
| `403` | 권한 부족 | `SecurityException` | 가맹점이 본사 API 호출, 타 가맹점 데이터 접근 |
| `409` | 상태 충돌 또는 중복 | `IllegalStateException` | 허용되지 않은 상태 전이, 중복 발주 |
| `500` | 서버 오류 | `Exception` | 처리되지 않은 내부 오류 |

## 4. User Service API

Base path: `/api/users`

### 4.1 회원가입

```http
POST /api/users/register
```

인증 없이 호출할 수 있다.

Request:

```json
{
  "email": "store.seoul@example.com",
  "password": "store1234!",
  "name": "서울역점 관리자",
  "role": "STUDENT"
}
```

| 필드 | 조건 |
|---|---|
| `email` | 필수, 이메일 형식, 중복 불가 |
| `password` | 필수, 8자 이상 |
| `name` | 필수, 공백 불가 |
| `role` | `STUDENT`(가맹점) 또는 `INSTRUCTOR`(본사) — **별칭이 아니라 DB 원본 값을 그대로 보낸다** |

Response `201 Created`:

```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다",
  "data": {
    "id": 10,
    "email": "store.seoul@example.com",
    "name": "서울역점 관리자",
    "role": "STUDENT",
    "createdAt": "2026-08-11T10:00:00"
  }
}
```

실서비스에서는 일반 회원가입 요청으로 `INSTRUCTOR`(본사)를 임의 생성하지 않도록 별도 절차를 두는 것을 권장하나, 현재는 검증 없이 그대로 생성된다.

### 4.2 내 정보 조회

```http
GET /api/users/me
```

`X-User-Id` 헤더로 식별. 인증된 사용자만 호출 가능.

### 4.3 사용자 조회

```http
GET /api/users/{id}
```

### 4.4 내부 API

```http
GET /api/users/internal/{id}
```

서비스 간 호출용. 별도 인증 없음.

## 5. Course(Product) Service API

Base path: `/api/courses` (경로명은 원본 템플릿 그대로 유지 — Gateway가 아는 라우트가 이 이름뿐이라 변경 불가)

### 5.1 상품 목록 조회

```http
GET /api/courses
```

권한: 인증된 사용자 누구나 (역할 구분 없음). 페이지네이션/필터 쿼리 파라미터는 없다 — 활성 상품 전체를 한 번에 반환한다.

Response item:

```json
{
  "id": 3,
  "title": "삼각김밥 전주비빔",
  "description": "...",
  "category": "FOOD",
  "price": 1500.00,
  "instructorId": 3,
  "enrollmentCount": 12,
  "status": "ACTIVE",
  "createdAt": "2026-08-01T09:00:00"
}
```

`category`는 `FOOD, DRINK, DAILY, FRESH, SNACK, HYGIENE, CHILLED, OTHER` 8종. `enrollmentCount`는 필드명은 원본 템플릿(수강 인원)을 유지했지만 실제 의미는 **가맹점 재고 수량**이다(입고 확인 시 증가).

### 5.2 상품 상세 조회

```http
GET /api/courses/{id}
```

### 5.3 카테고리별 상품 조회

```http
GET /api/courses/category/{category}
```

### 5.4 상품 등록

```http
POST /api/courses
```

권한: `INSTRUCTOR`(본사)만. `X-User-Id`를 `instructorId`로 저장한다.

Request:

```json
{
  "title": "생수 500ml",
  "description": "선택",
  "category": "DRINK",
  "price": 900
}
```

### 5.5 상품 수정

```http
PUT /api/courses/{id}
PATCH /api/courses/{id}   (하위 호환으로 같이 받지만 실제로 쓰지 않음)
```

권한: `INSTRUCTOR`(본사)만. 부분 수정(null 필드는 변경 안 함).

> **왜 PUT인가**: API Gateway(프리빌트, 소스 없음)가 `Origin` 헤더가 실린 `PATCH` 요청을 원인 불명으로 항상 `403 Forbidden` 처리하는 버그가 실측으로 확인됐다 (curl로 `Origin` 헤더 없이 보내면 200, 브라우저처럼 `Origin`을 실어 보내면 403 — GET/POST/PUT은 `Origin`이 있어도 정상 통과). 그래서 상품 수정은 `PUT`으로, 발주 상태 전이는 `POST`로 우회한다.

### 5.6 내부 API (Enrollment Service가 호출)

```http
GET  /api/courses/internal/exists/{id}              상품 존재 여부 (Boolean)
GET  /api/courses/internal/{id}                      상품 상세
POST /api/courses/internal/{id}/enrollment-count?quantity=30  재고 +quantity (입고 확인 시 호출)
GET  /api/courses/internal/recommend                 추천 서비스용 후보 조회
```

## 6. Enrollment(Order) Service API

Base path: `/api/enrollments`

발주는 요청마다 **상품 1종**과 `quantity` 1~999개를 지정한다. 같은 상품의 기존 발주가 `RECEIVED` 또는 `REJECTED`로 종료되면 새 발주를 만들 수 있으며, `REQUESTED` 또는 `APPROVED` 발주가 진행 중일 때만 중복 요청을 차단한다. v1.0 초안의 `items[]` 장바구니형 다건 발주와 `unitPrice`, `totalAmount` 가격 스냅샷은 구현되지 않았다.

### 6.1 발주 요청 (가맹점)

```http
POST /api/enrollments
```

권한: `STUDENT`(가맹점)

Request:

```json
{ "courseId": 3, "quantity": 30 }
```

`quantity`를 생략한 기존 요청은 하위 호환을 위해 1개로 처리한다. 허용 범위는 1~999이다.

처리 규칙: ① 상품 존재 확인(course-service 내부 호출) → ② 동일 상품의 진행 중 발주(`REQUESTED`, `APPROVED`) 여부 확인 → ③ 진행 중 발주가 없으면 새 `REQUESTED` 발주 저장. **이 시점에는 결제/정산이 발생하지 않는다.**

Response `201 Created`:

```json
{
  "success": true,
  "message": "발주가 접수되었습니다",
  "data": {
    "id": 17,
    "userId": 4,
    "courseId": 3,
    "quantity": 30,
    "status": "REQUESTED",
    "rejectReason": null,
    "createdAt": "2026-08-11T04:32:21",
    "updatedAt": "2026-08-11T04:32:21",
    "product": null
  }
}
```

### 6.2 내 발주 목록 조회 (가맹점)

```http
GET /api/enrollments/my?status=REQUESTED
```

`status`는 생략 가능. 응답의 각 항목에 `product`(상품 요약 정보)가 함께 채워져서 내려온다.

### 6.3 발주 상세 조회

```http
GET /api/enrollments/{orderId}
```

권한: 가맹점은 본인 발주만, 본사는 전체 조회 가능.

### 6.4 입고 확인 (가맹점)

```http
POST /api/enrollments/{orderId}/receive
```

권한: `STUDENT`(가맹점), 본인 발주만. 조건: 현재 상태가 `APPROVED`.

처리 규칙: `APPROVED → RECEIVED` 전이 → course-service에 재고 `+quantity` 요청 → 수량을 포함한 `order.received` Kafka 이벤트 발행.

### 6.5 전체 발주 목록 조회 (본사)

```http
GET /api/enrollments/admin?status=REQUESTED&storeId=10
```

권한: `INSTRUCTOR`(본사)만.

### 6.6 발주 승인 (본사)

```http
POST /api/enrollments/admin/{orderId}/approve
```

권한: `INSTRUCTOR`(본사)만. 조건: 현재 상태가 `REQUESTED`.

처리 규칙: `REQUESTED → APPROVED` 전이 커밋 → **곧바로 payment-service에 정산 요청** (상품의 현재 공급가 기준, 7절 참고). 정산 요청이 실패해도 승인 자체는 취소되지 않고 로그만 남긴다 (재시도 큐 없음 — 알려진 제약, 12.3절).

### 6.7 발주 반려 (본사)

```http
POST /api/enrollments/admin/{orderId}/reject
```

권한: `INSTRUCTOR`(본사)만. 조건: 현재 상태가 `REQUESTED`.

Request:

```json
{ "reason": "재고 일시 부족으로 이번 발주는 반려합니다" }
```

`reason`은 필수, 공백 불가. **`enrollments.reject_reason` 컬럼에 저장되고, 발주 목록/상세 응답의 `rejectReason` 필드로 가맹점도 조회할 수 있다.**

Response `200 OK`:

```json
{
  "success": true,
  "message": "발주가 반려되었습니다",
  "data": {
    "orderId": 17,
    "userId": 4,
    "courseId": 3,
    "status": "REJECTED",
    "rejectReason": "재고 일시 부족으로 이번 발주는 반려합니다",
    "updatedAt": "2026-08-11T04:32:22"
  }
}
```

### 6.8 내부 API

```http
GET /api/enrollments/internal/store/{storeId}/received
```

가맹점이 입고 완료(`RECEIVED`)한 상품 ID 목록. recommend-service가 추천 후보를 고를 때 호출한다.

## 7. Payment(정산) Service API

Base path: `/api/payments`

v1.0 초안은 "결제는 B2B MVP에서 제외"라고 명시했지만, 실제로는 **본사가 발주를 승인하는 순간 자동으로 발생하는 가맹점→본사 정산**으로 구현되어 있다. 실습 환경이라 실제 PG(카드사) 연동은 없고, 요청이 들어오면 UUID를 트랜잭션 ID로 발급해 **항상 즉시 `COMPLETED` 처리**하는 모의(mock) 결제다.

### 7.1 정산이 발생하는 시점 (중요)

```text
가맹점이 발주 요청 (POST /api/enrollments)           -> 결제 없음, 상태 REQUESTED
본사가 발주 승인 (POST /api/enrollments/admin/{id}/approve)
  -> enrollment-service가 course-service에서 현재 공급가 조회
  -> enrollment-service가 공급가 × 발주 수량을 계산해 payment-service에 정산 요청
  -> payment-service가 Payment 레코드 생성, 즉시 COMPLETED 처리
  -> payment.completed Kafka 이벤트 발행 -> enrollment-service가 구독해 정합성 로그만 남김
가맹점이 입고 확인 (POST /api/enrollments/{id}/receive) -> 재고만 증가, 정산과 무관
```

즉 **발주 "요청" 시점이 아니라 "승인" 시점에 결제가 들어간다.** 반려된 발주는 정산이 아예 발생하지 않는다. 정산 금액은 **승인 시점의 상품 현재 공급가 × 발주 수량**으로 계산된다 (v1.0 초안의 "발주 당시 가격 스냅샷 보존" 원칙과 다름 — 알려진 차이, 12.2절).

### 7.2 정산 단건 조회

```http
GET /api/payments/{id}
```

권한: 본사는 전체, 가맹점은 본인 소유 정산만 (다른 가맹점 것 조회 시 `403`).

Response:

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "paymentId": 12,
    "userId": 4,
    "courseId": 14,
    "amount": 1800.00,
    "status": "COMPLETED",
    "transactionId": "8e0d950c-5ef8-401e-b9c0-cf01a5cf7c08",
    "createdAt": "2026-08-11T02:28:47"
  }
}
```

`status`는 `PENDING`, `COMPLETED`, `FAILED`, `CANCELLED` 중 하나 (모의 결제라 사실상 `COMPLETED`만 나온다).

### 7.3 가맹점별 정산 내역 조회

```http
GET /api/payments/user/{userId}
```

권한: 본사는 아무 가맹점이나, 가맹점은 본인 것만 (`X-User-Id`와 `{userId}`가 다르면 `403`).

### 7.4 전체 정산 내역 조회 (본사)

```http
GET /api/payments/admin
```

권한: `INSTRUCTOR`(본사)만. 최신순 정렬.

### 7.5 내부 API

```http
POST /api/payments/internal/request
```

enrollment-service가 발주 승인 시 호출. 별도 인증 헤더를 요구하지 않는다 (Gateway를 거치지 않는 컨테이너 간 직접 호출이라 `X-User-*` 헤더 자체가 없음).

Request:

```json
{ "userId": 4, "courseId": 3, "amount": 1500 }
```

### 7.6 월별 정산 집계 API

**별도 백엔드 API는 없다.** 프론트엔드(`PaymentView.vue`)가 `GET /api/payments/user/{userId}` 또는 `/api/payments/admin`으로 받은 전체 목록을 클라이언트에서 이번 달(`createdAt` 기준) 것만 필터링해 합산하는 방식으로 "이번 달 정산 예정 금액" 요약을 보여준다. 실제 편의점 프랜차이즈(CU/GS25 등)는 한 달 매출·매입을 마감한 뒤 익월 중순께 로열티 등을 정산해 입금하는 월 단위 배치 정산을 쓰지만, 이 프로젝트는 승인 즉시 개별 건을 정산 완료 처리하는 단순화된 모델이다. 실제 배치 정산으로 확장하려면 8.3절 참고.

## 8. Recommend Service API

Base path: `/api/recommend` (FastAPI, Python)

### 8.1 가맹점 추천 조회

```http
GET /api/recommend/{storeId}
```

권한: 인증된 사용자 (JWT 검증만, 역할 구분 없음).

처리 규칙: course-service에서 활성 상품, enrollment-service에서 해당 가맹점의 입고 이력을 모아 안전한 후보를 규칙 기반으로 우선 선정한 뒤, `GEMINI_API_KEY` 환경변수가 설정돼 있으면 Gemini가 그 후보 안에서 순위/추천 사유를 보완한다. 키가 없거나 AI 호출에 실패하면 규칙 기반 결과를 그대로 반환한다 (`analysisMode: "RULE_BASED" | "AI"`).

Response:

```json
{
  "userId": 4,
  "recommendedCourses": [ ],
  "recommendations": [
    {
      "product": { "id": 3, "title": "삼각김밥 전주비빔", "category": "FOOD", "price": 1500.0, "...": "..." },
      "score": 88,
      "reason": "최근 자주 발주한 카테고리와 일치합니다",
      "signals": ["카테고리 일치", "현재 발주 가능"]
    }
  ],
  "basedOnCategory": "FOOD",
  "message": "입고 이력과 상품 데이터를 기준으로 계산한 발주 추천입니다.",
  "analysisMode": "RULE_BASED",
  "model": null
}
```

## 9. 서비스 간 통신 계약

동기 REST(WebClient, 컨테이너 네트워크 직접 호출)만 사용한다. 비동기는 Kafka 토픽 `payment.completed` 하나뿐이다.

| 호출 주체 | 대상 | 내부 API | 목적 |
|---|---|---|---|
| Enrollment Service | Course Service | `GET /api/courses/internal/exists/{id}` | 발주 생성 전 상품 존재 확인 |
| Enrollment Service | Course Service | `GET /api/courses/internal/{id}` | 발주 목록/상세에 상품 정보 첨부, 승인 시 정산 금액(공급가) 조회 |
| Enrollment Service | Course Service | `POST /api/courses/internal/{id}/enrollment-count?quantity={quantity}` | 입고 확인 시 재고 +발주 수량 |
| Enrollment Service | Payment Service | `POST /api/payments/internal/request` | 발주 승인 시 정산 요청 |
| Recommend Service | Course Service | `GET /api/courses/internal/recommend` | 추천 후보 조회 |
| Recommend Service | Enrollment Service | `GET /api/enrollments/internal/store/{storeId}/received` | 입고 이력 조회 |
| Payment Service | Kafka(`payment.completed`) | 이벤트 발행 | 정산 완료 알림 |
| Enrollment Service | Kafka(`payment.completed`) | 이벤트 구독 | 정산 완료와 발주 상태 정합성 확인(로깅만) |

내부 API는 서비스 discovery(Eureka)로 호출하지만, v1.0 초안에 있던 `SCOPE_service.read` Client Credentials 인증은 **아직 적용되지 않았다** (12.3절 참고). 이 API들은 Gateway 라우트 자체가 `/api/courses/**` 등에 포함돼 있어 외부에서도 물리적으로는 호출 가능한 상태다.

## 10. 데이터 모델

### 10.1 Product (`courses` 테이블)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | 상품 ID |
| `title` | `String` | 상품명 |
| `description` | `String` | 설명 |
| `category` | `Enum` | `FOOD,DRINK,DAILY,FRESH,SNACK,HYGIENE,CHILLED,OTHER` |
| `price` | `BigDecimal` | 공급가 |
| `instructorId` | `Long` | 등록한 본사 관리자 ID |
| `enrollmentCount` | `Integer` | 가맹점 재고 수량 (입고 시 증가) |
| `status` | `Enum` | `ACTIVE`, `INACTIVE` |
| `createdAt` / `updatedAt` | `DateTime` | |

### 10.2 Enrollment (`enrollments` 테이블, Order)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | 발주 ID |
| `userId` | `Long` | 가맹점 관리자 ID |
| `courseId` | `Long` | 상품 ID |
| `quantity` | `Integer` | 발주 수량(1~999, 기본값 1) |
| `status` | `Enum` | `REQUESTED,APPROVED,REJECTED,RECEIVED` |
| `rejectReason` | `String?` | 반려 사유 (반려된 경우에만 값 존재) |
| `createdAt` / `updatedAt` | `DateTime` | |

`(userId, courseId)`에는 일반 조회 인덱스만 사용한다. 동일 상품의 완료·반려 발주는 여러 건 이력으로 저장할 수 있고, 애플리케이션이 `REQUESTED`·`APPROVED` 상태의 동시 중복 발주를 차단한다.

### 10.3 Payment (`payments` 테이블)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | 정산 ID |
| `userId` | `Long` | 가맹점 관리자 ID |
| `courseId` | `Long` | 상품 ID |
| `amount` | `BigDecimal` | 정산 금액(승인 시점 공급가 × 발주 수량) |
| `status` | `Enum` | `PENDING,COMPLETED,FAILED,CANCELLED` |
| `transactionId` | `String` | 모의 PG 거래 ID (UUID) |
| `createdAt` / `updatedAt` | `DateTime` | |

## 11. 핵심 시나리오

```text
1. STUDENT(가맹점)이 GET /api/courses로 상품 확인
2. STUDENT가 POST /api/enrollments { courseId, quantity }로 발주 요청 -> REQUESTED (정산 없음)
3. INSTRUCTOR(본사)가 GET /api/enrollments/admin으로 요청 확인
4-a. POST /api/enrollments/admin/{id}/approve
     -> APPROVED, payment-service에 정산 요청 -> Payment COMPLETED 자동 생성
4-b. POST /api/enrollments/admin/{id}/reject { "reason": "..." }
     -> REJECTED, rejectReason 저장 (정산 없음)
5. STUDENT가 POST /api/enrollments/{id}/receive (APPROVED 건만)
   -> RECEIVED, courses.enrollmentCount +quantity
6. STUDENT/INSTRUCTOR가 GET /api/payments/user/{id} 또는 /api/payments/admin으로 정산 내역 확인
7. STUDENT가 GET /api/recommend/{storeId}로 다음 발주 추천 확인
```

## 12. 알려진 제약과 설계상 결정 사항

기획서/발표 자료에는 "왜 이렇게 만들었는지"를 설명할 때 이 절을 근거로 쓰면 된다.

### 12.1 다건 발주(장바구니) 미지원

v1.0 초안은 여러 상품을 한 번에 담는 `items[]` 구조를 가정했지만, 실제 구현은 **요청 1건당 상품 1종**이고 `quantity`로 1~999개를 지정한다. 이전 발주가 완료(`RECEIVED`)되거나 반려(`REJECTED`)되면 동일 상품을 다시 발주할 수 있지만, 여러 상품을 한 번에 묶는 장바구니형 발주는 지원하지 않는다.

### 12.2 정산 금액이 발주 시점이 아닌 승인 시점 가격 기준

발주 요청 시점의 가격을 스냅샷으로 저장하지 않는다. 본사가 승인하기 전에 상품 가격을 바꾸면, 정산 금액은 **승인 시점의 최신 가격**으로 계산된다. 실제 서비스라면 발주 시점 가격을 고정하는 것이 일반적이다.

### 12.3 내부 API에 서비스 간 인증이 없음

`/api/*/internal/**` 경로들은 Client Credentials 같은 별도 인증 없이 열려 있다. Gateway 라우트를 세분화할 수 없어서 외부에서도 물리적으로 호출 가능한 상태이며, `payment-service`의 `SecurityConfig`도 `anyRequest().permitAll()`이다. 실서비스라면 내부 API는 별도 네트워크 분리 또는 서비스 토큰 검증이 필요하다.

### 12.4 승인/반려/입고/상품수정에 PATCH 대신 POST·PUT을 쓰는 이유

API Gateway(프리빌트, 소스 없음)가 `Origin` 헤더가 포함된 `PATCH` 요청을 원인 불명으로 항상 `403 Forbidden` 처리한다. curl로 `Origin` 없이 보내면 정상 통과하지만, 브라우저는 항상 `Origin`을 자동으로 실어 보내기 때문에 실사용 환경에서는 100% 재현된다. GET/POST/PUT은 `Origin`이 있어도 영향받지 않는 것을 실측으로 확인했다. Gateway 자체를 고칠 수 없어 발주 상태 전이는 `POST`, 상품 수정은 `PUT`으로 우회했다 (REST 컨벤션 관점에서도 승인/반려처럼 "행위(action)"에 가까운 API는 `POST`를 쓰는 것이 GitHub/Stripe 등 실제 API에서도 흔한 방식이라 큰 무리는 아니다).

### 12.5 정산이 승인 즉시 완료되는 모의(mock) 결제

실제 PG(카드사/결제대행사) 연동이 없고, 정산 요청이 들어오면 항상 UUID 트랜잭션 ID를 발급해 즉시 `COMPLETED` 처리한다. 실제 편의점 프랜차이즈는 한 달 매출·매입을 마감한 뒤 익월 중순께 로열티 등을 반영해 일괄 입금하는 월 단위 배치 정산을 쓴다. 이 프로젝트에서 "이번 달 정산 예정 금액"으로 보여주는 값은 이번 달에 발생한 개별 정산 건의 단순 합계이며, 실제 월마감 배치나 이체 프로세스는 구현하지 않았다.

### 12.6 승인 시 정산 요청 실패해도 승인은 유지됨

`payment-service` 호출이 실패해도 `enrollment-service`는 예외를 삼키고 로그만 남긴 뒤 승인 상태(`APPROVED`)는 그대로 유지한다. 재시도 큐나 보상 트랜잭션(Saga)은 없다. 정산 실패 건을 다시 처리하려면 수동 개입이 필요하다.

## 13. 제외 범위

이번 API v2.2에서도 다음은 다루지 않는다.

- 실제 PG 연동, 환불, 세금계산서, 월 단위 배치 정산/이체
- 배송 상태 추적(`SHIPPING`/`DELIVERED`), 운송장, 배송 위치
- 부분 배송 및 부분 입고
- 발주 수정 및 발주 취소
- 다건(장바구니) 발주
- 내부 API(서비스 간 호출)의 별도 인증(Client Credentials 등)
- 다중 창고 및 복수 배송지
- 재고 실사, 판매 이력(발주/입고 외의 재고 차감)

위 기능은 핵심 흐름이 안정화된 뒤 별도 API 버전으로 분리한다.
