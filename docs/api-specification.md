# B2B 편의점 본사-가맹점 발주 관리 시스템 API 명세서

버전: v1.0 (Draft)

작성 기준:

- `msa-practice_scenario.pdf`의 MSA 구성, OAuth2 인증, API Gateway, Eureka, REST 통신 원칙
- 본 프로젝트에서 합의한 B2B 발주 업무 흐름
- 구현 범위: 상품 조회 -> 발주 -> 승인/반려 -> 배송 -> 입고 -> 재고 반영

## 1. 시스템 개요

### 1.1 핵심 사용자

| 역할 | 설명 |
|---|---|
| `HEADQUARTERS_ADMIN` | 상품 관리, 발주 승인/반려, 배송 상태 관리 |
| `STORE_ADMIN` | 상품 조회, 발주 생성, 발주 상태 조회, 입고 확인 |

### 1.2 핵심 상태 흐름

```text
REQUESTED -> APPROVED -> SHIPPING -> DELIVERED -> RECEIVED
     |
     +------> REJECTED
```

허용되지 않은 상태 전이는 `409 Conflict`로 응답한다.

| 현재 상태 | 허용되는 다음 상태 | 변경 주체 |
|---|---|---|
| `REQUESTED` | `APPROVED`, `REJECTED` | 본사 관리자 |
| `APPROVED` | `SHIPPING` | 본사 관리자 |
| `SHIPPING` | `DELIVERED` | 본사 관리자 |
| `DELIVERED` | `RECEIVED` | 가맹점 관리자 |
| `RECEIVED` | 없음 | - |
| `REJECTED` | 없음 | - |

입고 처리는 `DELIVERED` 상태에서 한 번만 가능하다. 재고 증가는 `RECEIVED` 상태 전환이 성공한 경우에만 수행한다.

## 2. MSA 및 라우팅

### 2.1 외부 요청 흐름

```text
Client
  -> Auth Server : OAuth2 Authorization Code 로그인
  -> API Gateway : Bearer Access Token 전달
  -> 각 서비스 : Gateway가 JWT 검증 후 라우팅
```

모든 외부 API 요청은 API Gateway를 통과한다.

| 구성 요소 | 기본 포트 | 책임 |
|---|---:|---|
| Auth Server | 9000 | OAuth2 로그인, Authorization Code, JWT 발급 |
| API Gateway | 8080 | 단일 진입점, 라우팅, JWT 검증, 사용자 헤더 전달 |
| User Service | 8081 | 사용자와 역할 관리 |
| Product Service | 8082 | 상품 등록, 수정, 조회 |
| Order Service | 8083 | 발주 및 상태 전이 관리 |
| Inventory Service | 8084 | 가맹점별 재고 관리 |
| Eureka Server | 8761 | 서비스 등록 및 탐색 |
| MariaDB | 3379(local) / 3306(container) | 서비스별 업무 데이터 저장 |

### 2.2 Gateway 라우팅

| 외부 경로 | 대상 서비스 |
|---|---|
| `/api/users/**` | User Service |
| `/api/products/**` | Product Service |
| `/api/orders/**` | Order Service |
| `/api/admin/orders/**` | Order Service |
| `/api/inventories/**` | Inventory Service |
| `/oauth2/**`, `/login/**`, `/userinfo` | Auth Server |

서비스 내부 호출용 `/internal/**` 경로는 Gateway 외부 공개 대상이 아니다. 서비스 간 호출은 Eureka 기반 REST 클라이언트와 OAuth2 Client Credentials를 사용한다.

## 3. 공통 규칙

### 3.1 기본 URL

```text
외부 API: http://localhost:8080
Content-Type: application/json
문자 인코딩: UTF-8
```

### 3.2 인증 헤더

인증이 필요한 API는 다음 헤더를 전송한다.

```http
Authorization: Bearer {access_token}
```

Gateway는 JWT 검증 후 다음 헤더를 하위 서비스에 전달한다.

```http
X-User-Id: 1
X-User-Email: store@example.com
X-User-Role: STORE_ADMIN
```

클라이언트는 `X-User-*` 헤더를 직접 설정하지 않는다. 하위 서비스는 Gateway 외부에서 전달된 사용자 식별 헤더를 신뢰하지 않도록 네트워크를 제한해야 한다.

### 3.3 OAuth2 엔드포인트

PDF 시나리오의 Authorization Code 흐름을 따른다.

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/oauth2/authorize` | 로그인 및 인증 코드 요청 |
| `POST` | `/oauth2/token` | Authorization Code를 Access Token으로 교환 |
| `GET` | `/oauth2/jwks` | Resource Server가 JWT 검증에 사용하는 공개키 |
| `GET` | `/.well-known/openid-configuration` | OIDC 메타데이터 |

프론트엔드 OAuth Client 설정 예시는 다음과 같다.

```text
client_id: web-client
grant_type: authorization_code
scope: openid profile read write
redirect_uri: http://localhost:3000/callback
```

### 3.4 성공 응답

단건 응답은 서비스별 리소스를 `data`에 담는다.

```json
{
  "success": true,
  "message": "성공",
  "data": {}
}
```

목록 응답은 `items`와 페이지 정보를 함께 반환한다.

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

### 3.5 오류 응답

```json
{
  "success": false,
  "message": "발주 상태가 APPROVED가 아니므로 배송을 시작할 수 없습니다",
  "data": null
}
```

| HTTP 상태 | 의미 | 대표 코드 또는 상황 |
|---:|---|---|
| `400` | 요청 형식 또는 필드 검증 실패 | 필수값 누락, 수량이 0 이하 |
| `401` | 인증 실패 | Access Token 누락 또는 만료 |
| `403` | 권한 부족 | 가맹점 관리자가 본사 API 호출 |
| `404` | 리소스 없음 | 존재하지 않는 상품/발주 |
| `409` | 상태 충돌 또는 중복 | 허용되지 않은 상태 전이, 중복 입고 |
| `422` | 업무 규칙 위반 | 비활성 상품 발주, 발주 가능하지 않은 상품 |
| `500` | 서버 오류 | 처리되지 않은 내부 오류 |

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
  "name": "서울역점 관리자",
  "email": "store.seoul@example.com",
  "password": "store1234",
  "role": "STORE_ADMIN"
}
```

Validation:

| 필드 | 조건 |
|---|---|
| `name` | 필수, 공백 불가 |
| `email` | 필수, 이메일 형식, 중복 불가 |
| `password` | 필수, 8자 이상 |
| `role` | `HEADQUARTERS_ADMIN` 또는 `STORE_ADMIN` |

Response `201 Created`:

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "id": 10,
    "name": "서울역점 관리자",
    "email": "store.seoul@example.com",
    "role": "STORE_ADMIN",
    "createdAt": "2026-08-10T10:00:00"
  }
}
```

실서비스에서는 일반 회원가입 요청으로 `HEADQUARTERS_ADMIN`을 임의 생성하지 않도록 별도의 관리자 초대 또는 초기 데이터 절차를 둔다.

### 4.2 내 정보 조회

```http
GET /api/users/me
```

권한: 인증된 사용자

Response `200 OK`:

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "id": 10,
    "name": "서울역점 관리자",
    "email": "store.seoul@example.com",
    "role": "STORE_ADMIN"
  }
}
```

## 5. Product Service API

Base path: `/api/products`

### 5.1 상품 목록 조회

```http
GET /api/products?category=DRINK&active=true&page=0&size=20
```

권한: `HEADQUARTERS_ADMIN`, `STORE_ADMIN`

Query parameters:

| 이름 | 필수 | 설명 |
|---|---|---|
| `category` | 아니오 | 카테고리 필터 |
| `keyword` | 아니오 | 상품명 검색 |
| `active` | 아니오 | 판매 가능 상품 여부, 기본값 `true` |
| `page` | 아니오 | 0부터 시작, 기본값 `0` |
| `size` | 아니오 | 기본값 `20`, 최대 `100` |

Response `200 OK`의 상품 item:

```json
{
  "id": 101,
  "name": "생수 500ml",
  "category": "DRINK",
  "supplyPrice": 500,
  "orderUnit": 1,
  "active": true
}
```

### 5.2 상품 상세 조회

```http
GET /api/products/{productId}
```

권한: `HEADQUARTERS_ADMIN`, `STORE_ADMIN`

### 5.3 상품 등록

```http
POST /api/products
```

권한: `HEADQUARTERS_ADMIN`

Request:

```json
{
  "name": "생수 500ml",
  "category": "DRINK",
  "supplyPrice": 500,
  "orderUnit": 1,
  "active": true
}
```

Validation:

| 필드 | 조건 |
|---|---|
| `name` | 필수, 공백 불가 |
| `category` | 필수 |
| `supplyPrice` | 0 이상 |
| `orderUnit` | 1 이상 정수 |
| `active` | 생략 시 `true` |

Response: `201 Created`

### 5.4 상품 수정

```http
PATCH /api/products/{productId}
```

권한: `HEADQUARTERS_ADMIN`

Request 예시:

```json
{
  "name": "생수 500ml 묶음",
  "supplyPrice": 550,
  "orderUnit": 1,
  "active": true
}
```

상품 가격을 수정해도 이미 생성된 발주의 `unitPrice`와 `amount`는 변경되지 않는다.

## 6. Order Service API

Base path: `/api/orders`

### 6.1 발주 생성

```http
POST /api/orders
```

권한: `STORE_ADMIN`

Request:

```json
{
  "items": [
    {
      "productId": 101,
      "quantity": 30
    },
    {
      "productId": 205,
      "quantity": 10
    }
  ]
}
```

처리 규칙:

1. Gateway의 `X-User-Id`로 가맹점을 식별한다.
2. Order Service가 Product Service에 상품 존재 여부와 발주 가능 여부를 확인한다.
3. 상품의 현재 `supplyPrice`를 `OrderItem.unitPrice`에 복사한다.
4. `amount = quantity * unitPrice`를 계산한다.
5. `totalAmount`를 합산하고 상태를 `REQUESTED`로 저장한다.

Response `201 Created`:

```json
{
  "success": true,
  "message": "발주가 접수되었습니다",
  "data": {
    "id": 9001,
    "storeId": 10,
    "status": "REQUESTED",
    "totalAmount": 15000,
    "rejectionReason": null,
    "requestedAt": "2026-08-10T10:30:00",
    "approvedAt": null,
    "shippingStartedAt": null,
    "deliveredAt": null,
    "receivedAt": null,
    "items": [
      {
        "id": 1,
        "productId": 101,
        "productName": "생수 500ml",
        "quantity": 30,
        "unitPrice": 500,
        "amount": 15000
      }
    ]
  }
}
```

### 6.2 내 발주 목록 조회

```http
GET /api/orders?status=REQUESTED&page=0&size=20
```

권한: `STORE_ADMIN`

인증된 가맹점의 발주만 반환한다. `storeId`를 Query parameter로 받아도 서버의 사용자 식별값보다 우선하지 않는다.

### 6.3 발주 상세 조회

```http
GET /api/orders/{orderId}
```

권한:

- `STORE_ADMIN`: 본인 가맹점 발주만 조회
- `HEADQUARTERS_ADMIN`: 전체 발주 조회

### 6.4 입고 확인

```http
PATCH /api/orders/{orderId}/receive
```

권한: `STORE_ADMIN`

Request body: 없음

처리 규칙:

- 발주자가 해당 가맹점 관리자여야 한다.
- 현재 상태가 `DELIVERED`여야 한다.
- 상태를 `RECEIVED`로 변경하고 `receivedAt`을 기록한다.
- 각 OrderItem의 수량만큼 Inventory Service에 재고 증가를 요청한다.
- 이미 `RECEIVED`인 발주는 다시 재고를 증가시키지 않는다.

Response `200 OK`:

```json
{
  "success": true,
  "message": "입고가 완료되고 재고가 반영되었습니다",
  "data": {
    "orderId": 9001,
    "status": "RECEIVED",
    "receivedAt": "2026-08-12T15:00:00"
  }
}
```

## 7. Headquarters Order Management API

Base path: `/api/admin/orders`

모든 API는 `HEADQUARTERS_ADMIN` 권한이 필요하다.

### 7.1 전체 발주 요청 조회

```http
GET /api/admin/orders?status=REQUESTED&storeId=10&page=0&size=20
```

지원 Query parameters:

| 이름 | 설명 |
|---|---|
| `status` | `REQUESTED`, `APPROVED`, `REJECTED`, `SHIPPING`, `DELIVERED`, `RECEIVED` |
| `storeId` | 특정 가맹점 필터 |
| `from` / `to` | 요청일 범위 |
| `page` / `size` | 페이지 조건 |

### 7.2 발주 승인

```http
PATCH /api/admin/orders/{orderId}/approve
```

Request body: 없음

조건: 현재 상태가 `REQUESTED`

Response `200 OK`:

```json
{
  "success": true,
  "message": "발주가 승인되었습니다",
  "data": {
    "orderId": 9001,
    "status": "APPROVED",
    "approvedAt": "2026-08-10T13:00:00"
  }
}
```

### 7.3 발주 반려

```http
PATCH /api/admin/orders/{orderId}/reject
```

Request:

```json
{
  "rejectionReason": "일시적인 재고 부족"
}
```

조건:

- 현재 상태가 `REQUESTED`
- `rejectionReason`은 필수이며 공백일 수 없다.

### 7.4 배송 시작

```http
PATCH /api/admin/orders/{orderId}/shipping
```

Request body: 없음

조건: 현재 상태가 `APPROVED`

서버는 `shippingStartedAt`을 기록하고 상태를 `SHIPPING`으로 변경한다.

### 7.5 배송 완료

```http
PATCH /api/admin/orders/{orderId}/delivered
```

Request body: 없음

조건: 현재 상태가 `SHIPPING`

서버는 `deliveredAt`을 기록하고 상태를 `DELIVERED`로 변경한다. 이 시점에는 재고를 증가시키지 않는다.

## 8. Inventory Service API

### 8.1 가맹점 재고 조회

```http
GET /api/inventories?productId=101
```

권한:

- `STORE_ADMIN`: 본인 가맹점 재고만 조회
- `HEADQUARTERS_ADMIN`: `storeId`를 지정해 가맹점 재고 조회 가능

Response `200 OK`:

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "items": [
      {
        "id": 7001,
        "storeId": 10,
        "productId": 101,
        "productName": "생수 500ml",
        "quantity": 50,
        "lastReceivedAt": "2026-08-12T15:00:00"
      }
    ]
  }
}
```

### 8.2 단일 상품 재고 조회

```http
GET /api/inventories/{productId}
```

권한: `STORE_ADMIN`, `HEADQUARTERS_ADMIN`

### 8.3 입고 재고 반영 - 내부 API

```http
POST /internal/inventories/receive
```

외부 Gateway에 공개하지 않는다. Order Service가 Client Credentials Access Token과 함께 호출한다.

Request:

```json
{
  "orderId": 9001,
  "storeId": 10,
  "items": [
    {
      "productId": 101,
      "quantity": 30
    }
  ]
}
```

처리 규칙:

- 동일한 `orderId`의 입고 반영은 한 번만 성공해야 한다.
- 기존 재고가 없으면 `(storeId, productId)` 기준으로 생성한다.
- 기존 재고가 있으면 `quantity += receivedQuantity`로 증가시킨다.
- 성공 시 `lastReceivedAt`을 갱신한다.

## 9. 서비스 간 통신 계약

PDF 시나리오의 REST 기반 동기 호출 원칙을 적용한다.

| 호출 주체 | 대상 | 내부 API | 목적 |
|---|---|---|---|
| Order Service | Product Service | `GET /internal/products/{id}/orderable` | 상품 존재, 활성 상태, 현재 공급가 확인 |
| Order Service | Inventory Service | `POST /internal/inventories/receive` | 입고 확정 후 재고 증가 |

### 9.1 상품 발주 가능 여부 조회

```http
GET /internal/products/101/orderable
Authorization: Bearer {service_access_token}
```

Response:

```json
{
  "productId": 101,
  "name": "생수 500ml",
  "supplyPrice": 500,
  "orderUnit": 1,
  "active": true,
  "orderable": true
}
```

서비스 간 토큰에는 최소 `SCOPE_service.read` 권한을 사용한다. 내부 API를 Gateway 외부 공개 경로와 분리한다.

## 10. 데이터 모델

### 10.1 Product

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | 상품 ID |
| `name` | `String` | 상품명 |
| `category` | `String` | 상품 카테고리 |
| `supplyPrice` | `Long` 또는 `BigDecimal` | 현재 공급가 |
| `orderUnit` | `Integer` | 최소 발주 단위 |
| `active` | `Boolean` | 발주 가능 여부 |

### 10.2 Order

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | 발주 ID |
| `storeId` | `Long` | 가맹점 ID |
| `status` | `OrderStatus` | 발주 상태 |
| `totalAmount` | `BigDecimal` | 발주 당시 항목 금액 합계 |
| `rejectionReason` | `String?` | 반려 사유 |
| `requestedAt` | `DateTime` | 발주 요청 시각 |
| `approvedAt` | `DateTime?` | 승인 시각 |
| `shippingStartedAt` | `DateTime?` | 배송 시작 시각 |
| `deliveredAt` | `DateTime?` | 배송 완료 시각 |
| `receivedAt` | `DateTime?` | 입고 확인 시각 |

### 10.3 OrderItem

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | 발주 항목 ID |
| `orderId` | `Long` | 발주 ID |
| `productId` | `Long` | 상품 ID |
| `quantity` | `Integer` | 발주 수량 |
| `unitPrice` | `BigDecimal` | 발주 당시 공급가 Snapshot |
| `amount` | `BigDecimal` | `quantity * unitPrice` |

### 10.4 StoreInventory

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | 재고 ID |
| `storeId` | `Long` | 가맹점 ID |
| `productId` | `Long` | 상품 ID |
| `quantity` | `Integer` | 현재 재고 수량 |
| `lastReceivedAt` | `DateTime?` | 마지막 입고 시각 |

재고 테이블은 `(storeId, productId)`를 유일 키로 둔다.

## 11. 핵심 시나리오

```text
1. STORE_ADMIN이 POST /api/orders로 발주 요청
2. Order Service가 Product Service에서 상품 가격과 active 상태 확인
3. Order Service가 unitPrice Snapshot을 저장하고 REQUESTED 응답
4. HEADQUARTERS_ADMIN이 GET /api/admin/orders로 요청 확인
5. PATCH /api/admin/orders/{id}/approve
6. PATCH /api/admin/orders/{id}/shipping
7. PATCH /api/admin/orders/{id}/delivered
8. STORE_ADMIN이 PATCH /api/orders/{id}/receive
9. Order Service가 Inventory Service에 내부 재고 반영 요청
10. STORE_ADMIN이 GET /api/inventories로 증가한 재고 확인
```

### 11.1 발주 당시 가격 보존 예시

```text
발주 시 supplyPrice = 500
OrderItem.unitPrice = 500 저장
이후 상품 supplyPrice = 550으로 수정
기존 OrderItem.unitPrice는 500 유지
```

### 11.2 재고 증가 예시

```text
입고 전 재고: 20
입고 수량: 30
입고 완료 후 재고: 50
```

## 12. 구현 시 서비스 배치

첨부 PDF의 기존 온라인 강의 서비스 명칭을 그대로 확장하지 않고, B2B 도메인에 맞는 책임으로 매핑한다.

| 현재 구조 또는 기반 | 목표 책임 |
|---|---|
| `user-service` | User Service로 재사용 |
| `course-service` | Product Service 도메인으로 전환 또는 별도 Product Service 분리 |
| `enrollment-service` | Order Service 도메인으로 전환 또는 별도 Order Service 분리 |
| `payment-service` | B2B MVP에서는 제외하거나 별도 결제 기능으로 보류 |
| `recommend-service` | B2B MVP 범위에서 제외 |
| `auth-server`, `api-gateway`, `eureka-server` | PDF의 인증/라우팅/서비스 탐색 구조 재사용 |
| Inventory Service | 별도 서비스로 추가 |

최소 구현 순서는 다음과 같다.

```text
User/Auth/Gateway 확인
  -> Product API
  -> Order 생성 및 상태 전이
  -> Inventory API
  -> 가맹점/본사 시연 화면 연결
```

## 13. 제외 범위

이번 API v1에서는 다음을 다루지 않는다.

- 결제 승인, 환불, 세금계산서
- 배송 기사 배정, 운송장, 배송 위치 추적
- 부분 배송 및 부분 입고
- 발주 수정 및 발주 취소
- 재고 차감, 판매 이력, 재고 실사
- 추천 알고리즘
- 다중 창고 및 복수 배송지

위 기능은 핵심 상태 흐름이 안정화된 뒤 별도 API 버전 또는 기능 명세로 분리한다.
