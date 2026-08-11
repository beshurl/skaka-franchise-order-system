"""
데모/개발용 상품 더미 데이터 시딩 스크립트

- docker compose로 전체 스택(특히 api-gateway:8080, auth-server)이 떠있는 상태에서 실행합니다.
- 테스트용 본사 계정(seed-hq@example.com)으로 회원가입 -> 로그인 -> 카테고리 8종 x 20개,
  총 161개 상품을 실제 API(POST /api/courses)를 통해 등록합니다. (DB에 직접 INSERT 아님)
- 이미 등록된 상품과 제목이 겹치면 건너뛰므로 여러 번 실행해도 중복 생성되지 않습니다.
- 가격은 카테고리 랜덤이 아니라 상품별로 직접 정한 값입니다 (용량/종류가 비슷하면
  비슷한 가격, 생수 500ml < 2L처럼 크기 관계도 반영).

실행 방법:
    python3 scripts/seed-products.py
"""

import base64
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = "http://localhost:8080"
SEED_EMAIL = "seed-hq@example.com"
SEED_PASSWORD = "SeedHq1234!"
CLIENT_ID = "web-client"
CLIENT_SECRET = "web-secret"
REDIRECT_URI = "http://localhost:3000/callback"

# 상품명 -> (카테고리, 가격). 가격은 실제 편의점 시세를 참고해 개별적으로 정했습니다.
PRODUCTS = {
    # FOOD
    "삼각김밥 참치마요": ("FOOD", 1500),
    "삼각김밥 전주비빔": ("FOOD", 1500),
    "치즈김밥": ("FOOD", 1700),
    "야채김밥": ("FOOD", 1700),
    "참치마요 도시락": ("FOOD", 4500),
    "제육볶음 도시락": ("FOOD", 5500),
    "돈까스 도시락": ("FOOD", 5000),
    "샌드위치 햄치즈": ("FOOD", 3200),
    "샌드위치 에그마요": ("FOOD", 2800),
    "컵라면 신라면": ("FOOD", 1500),
    "컵라면 진라면": ("FOOD", 1500),
    "컵라면 육개장": ("FOOD", 1600),
    "핫바 오리지널": ("FOOD", 1800),
    "핫바 매운맛": ("FOOD", 1800),
    "소시지빵": ("FOOD", 2200),
    "계란빵": ("FOOD", 1800),
    "주먹밥 멸치": ("FOOD", 1400),
    "버거 불고기": ("FOOD", 3500),
    "버거 치킨": ("FOOD", 3800),
    "떡볶이 컵": ("FOOD", 3000),
    # DRINK (용량 반영: 500ml < 2L, 캔 < 병 등)
    "생수 500ml": ("DRINK", 900),
    "생수 2L": ("DRINK", 1600),
    "코카콜라 500ml": ("DRINK", 2000),
    "사이다 500ml": ("DRINK", 2000),
    "이온음료 파워에이드": ("DRINK", 2000),
    "이온음료 포카리스웨트": ("DRINK", 2000),
    "캔커피 아메리카노": ("DRINK", 1500),
    "캔커피 라떼": ("DRINK", 1600),
    "오렌지주스": ("DRINK", 2000),
    "포도주스": ("DRINK", 2000),
    "에너지드링크 몬스터": ("DRINK", 2500),
    "에너지드링크 핫식스": ("DRINK", 1700),
    "흰우유 200ml": ("DRINK", 1400),
    "초코우유 200ml": ("DRINK", 1500),
    "바나나우유": ("DRINK", 1700),
    "두유 검은콩": ("DRINK", 1600),
    "탄산수 페리에": ("DRINK", 2500),
    "제로콜라": ("DRINK", 2000),
    "옥수수수염차": ("DRINK", 1600),
    "보리차 500ml": ("DRINK", 1300),
    # DAILY
    "칫솔": ("DAILY", 2000),
    "치약 미니": ("DAILY", 2500),
    "두루마리 화장지": ("DAILY", 1500),
    "물티슈 소형": ("DAILY", 2000),
    "건전지 AA 4구": ("DAILY", 4500),
    "건전지 AAA 4구": ("DAILY", 4500),
    "비닐우산": ("DAILY", 3000),
    "1회용 라이터": ("DAILY", 1500),
    "1회용 면도기": ("DAILY", 2000),
    "덧신 세트": ("DAILY", 3000),
    "마스크 KF94": ("DAILY", 1500),
    "충전 케이블 C타입": ("DAILY", 5000),
    "충전 케이블 8핀": ("DAILY", 5000),
    "볼펜": ("DAILY", 1000),
    "메모지": ("DAILY", 1500),
    "종량제봉투 20L": ("DAILY", 3000),
    "위생장갑": ("DAILY", 2000),
    "고무장갑": ("DAILY", 3500),
    "청소포": ("DAILY", 3000),
    "면봉": ("DAILY", 2000),
    # FRESH
    "바나나 3입": ("FRESH", 2500),
    "사과 2입": ("FRESH", 3000),
    "방울토마토": ("FRESH", 3500),
    "샐러드 컵": ("FRESH", 4000),
    "두부 1모": ("FRESH", 1800),
    "계란 6구": ("FRESH", 3000),
    "무농약 계란 10구": ("FRESH", 5500),
    "요거트 플레인": ("FRESH", 1800),
    "그릭요거트": ("FRESH", 2500),
    "저지방우유 900ml": ("FRESH", 2800),
    "김치 소포장": ("FRESH", 3500),
    "깻잎 반찬": ("FRESH", 3000),
    "멸치볶음 반찬": ("FRESH", 3500),
    "진미채 반찬": ("FRESH", 3800),
    "슬라이스 치즈": ("FRESH", 3000),
    "베이컨": ("FRESH", 4500),
    "구운계란 2입": ("FRESH", 1500),
    "닭가슴살 슬라이스": ("FRESH", 4000),
    "떠먹는요거트 4입": ("FRESH", 3200),
    "저염 김": ("FRESH", 2500),
    # SNACK
    "감자칩 오리지널": ("SNACK", 2000),
    "감자칩 매운맛": ("SNACK", 2000),
    "초코바 킷캣": ("SNACK", 1500),
    "초코바 스니커즈": ("SNACK", 1500),
    "젤리 하리보": ("SNACK", 2000),
    "젤리 마이구미": ("SNACK", 2000),
    "사탕 알사탕": ("SNACK", 1000),
    "껌 자일리톨": ("SNACK", 1500),
    "비스킷 크래커": ("SNACK", 2000),
    "새우깡": ("SNACK", 1700),
    "포카칩": ("SNACK", 2000),
    "나쵸칩": ("SNACK", 2500),
    "초콜릿 가나": ("SNACK", 2000),
    "초콜릿 킨더": ("SNACK", 3000),
    "쿠키 초코칩": ("SNACK", 2500),
    "군것질 육포": ("SNACK", 3500),
    "오징어땅콩": ("SNACK", 1700),
    "팝콘 카라멜": ("SNACK", 2000),
    "그래놀라바": ("SNACK", 1500),
    "허니버터칩": ("SNACK", 2000),
    # HYGIENE
    "손소독제 소형": ("HYGIENE", 2500),
    "물티슈 휴대용": ("HYGIENE", 1500),
    "일회용 밴드": ("HYGIENE", 2000),
    "생리대 중형": ("HYGIENE", 3500),
    "생리대 대형": ("HYGIENE", 4000),
    "비누 고체형": ("HYGIENE", 2000),
    "핸드크림": ("HYGIENE", 3000),
    "립밤": ("HYGIENE", 2500),
    "구강청결제 소형": ("HYGIENE", 2000),
    "치실": ("HYGIENE", 2500),
    "면봉 소포장": ("HYGIENE", 1500),
    "손톱깎이": ("HYGIENE", 3000),
    "탈취제 스프레이": ("HYGIENE", 4000),
    "탈모샴푸 소포장": ("HYGIENE", 2500),
    "샤워타올": ("HYGIENE", 1500),
    "일회용 칫솔세트": ("HYGIENE", 2000),
    "여성청결제": ("HYGIENE", 4500),
    "핫팩": ("HYGIENE", 1500),
    "쿨패치": ("HYGIENE", 2000),
    "마스크 KF80": ("HYGIENE", 1000),
    # CHILLED
    "아이스크림 바닐라": ("CHILLED", 2000),
    "아이스크림 초코": ("CHILLED", 2000),
    "붕어싸만코": ("CHILLED", 1800),
    "메로나": ("CHILLED", 1200),
    "설레임": ("CHILLED", 1500),
    "냉동만두 고기": ("CHILLED", 5500),
    "냉동만두 김치": ("CHILLED", 5000),
    "냉동피자 콤비": ("CHILLED", 6500),
    "냉동핫도그": ("CHILLED", 4000),
    "냉동주먹밥": ("CHILLED", 3500),
    "요거트 아이스바": ("CHILLED", 1500),
    "젤라또 컵": ("CHILLED", 3500),
    "빙수 컵": ("CHILLED", 4000),
    "냉동볶음밥": ("CHILLED", 4500),
    "냉동떡볶이": ("CHILLED", 4000),
    "치즈스틱 냉동": ("CHILLED", 4500),
    "탕수육 냉동": ("CHILLED", 6500),
    "냉동감자튀김": ("CHILLED", 3500),
    "냉동새우튀김": ("CHILLED", 5000),
    "푸딩 컵": ("CHILLED", 2000),
    # OTHER
    "로또 복권": ("OTHER", 1000),
    "즉석복권 스피또": ("OTHER", 2000),
    "문화상품권 1만원": ("OTHER", 10000),
    "우표 기본형": ("OTHER", 430),
    "택배봉투 소형": ("OTHER", 1000),
    "우산 3단": ("OTHER", 8000),
    "휴대폰 거치대": ("OTHER", 5000),
    "보조배터리 5000mAh": ("OTHER", 15000),
    "이어폰 유선": ("OTHER", 5000),
    "라이터 지포형": ("OTHER", 3000),
    "건전지 9V": ("OTHER", 3000),
    "USB 메모리 32GB": ("OTHER", 12000),
    "라이트 미니손전등": ("OTHER", 5000),
    "카드지갑": ("OTHER", 8000),
    "미니선풍기": ("OTHER", 12000),
    "핸드워머": ("OTHER", 1500),
    "안대": ("OTHER", 2000),
    "귀마개": ("OTHER", 1500),
    "휴대용 우산비닐": ("OTHER", 500),
    "종이컵 20입": ("OTHER", 2000),
}


def http_json(method, path, token=None, body=None):
    url = f"{BASE_URL}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())


def get_access_token():
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

    authorize_url = (
        f"{BASE_URL}/oauth2/authorize?response_type=code&client_id={CLIENT_ID}"
        f"&redirect_uri={REDIRECT_URI}&scope=openid%20profile%20read%20write"
    )
    opener.open(authorize_url).read()  # 로그인 폼으로 리다이렉트

    login_data = urllib.parse.urlencode(
        {"username": SEED_EMAIL, "password": SEED_PASSWORD}
    ).encode()
    login_req = urllib.request.Request(f"{BASE_URL}/login", data=login_data, method="POST")

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    no_redirect_opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(cj), NoRedirect
    )
    try:
        no_redirect_opener.open(login_req)
    except urllib.error.HTTPError:
        pass  # 302는 정상 (에러 아님)

    try:
        resp = no_redirect_opener.open(authorize_url)
        location = resp.geturl()
    except urllib.error.HTTPError as e:
        location = e.headers.get("Location")

    code = location.split("code=")[1].split("&")[0]

    token_body = urllib.parse.urlencode(
        {
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": REDIRECT_URI,
        }
    ).encode()
    basic = base64.b64encode(f"{CLIENT_ID}:{CLIENT_SECRET}".encode()).decode()
    token_req = urllib.request.Request(
        f"{BASE_URL}/oauth2/token",
        data=token_body,
        method="POST",
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "Authorization": f"Basic {basic}",
        },
    )
    with urllib.request.urlopen(token_req) as resp:
        return json.loads(resp.read())["access_token"]


def ensure_seed_account():
    try:
        http_json(
            "POST",
            "/api/users/register",
            body={
                "email": SEED_EMAIL,
                "password": SEED_PASSWORD,
                "name": "시딩용본사계정",
                "role": "HEADQUARTERS_ADMIN",
            },
        )
        print(f"시딩 계정 생성됨: {SEED_EMAIL}")
    except urllib.error.HTTPError as e:
        if e.code == 400:
            print(f"시딩 계정 이미 존재함: {SEED_EMAIL}")
        else:
            raise


def main():
    ensure_seed_account()
    token = get_access_token()

    existing = http_json("GET", "/api/courses", token=token)["data"]
    existing_titles = {p["title"] for p in existing}

    created, skipped = 0, 0
    for title, (category, price) in PRODUCTS.items():
        if title in existing_titles:
            skipped += 1
            continue
        body = {
            "title": title,
            "description": f"{category} 카테고리 데모 상품",
            "category": category,
            "price": price,
        }
        http_json("POST", "/api/courses", token=token, body=body)
        created += 1

    print(f"\n완료: 신규 {created}개 생성, 기존 {skipped}개 건너뜀")


if __name__ == "__main__":
    main()
