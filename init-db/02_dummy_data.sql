-- Storelink 시연용 더미데이터
-- 기존 네 개 테이블과 컬럼만 사용하며, 반복 실행해도 동일한 결과를 유지한다.

-- 이전 온라인 강의 이미지에서 생성된 상태 값을 발주 상태 값으로 정규화한다.
UPDATE enrollments SET status = 'REQUESTED' WHERE status = 'PENDING';
UPDATE enrollments SET status = 'RECEIVED' WHERE status = 'ACTIVE';
UPDATE enrollments SET status = 'REJECTED' WHERE status = 'CANCELLED';

-- 로그인 계정
-- 가맹점: store.demo@storelink.kr / store1234
-- 본사  : hq.demo@storelink.kr    / hq1234
INSERT INTO users (id, email, password, name, role, created_at, updated_at) VALUES
    (101, 'store.demo@storelink.kr', '$2y$10$jDwaYWe6mCeYSNyTgr9pHeENznb0iyoC9WP3G0U76c1LbhH3P.xqm', '상무점 관리자', 'STUDENT', NOW(6), NOW(6)),
    (102, 'hq.demo@storelink.kr', '$2y$10$9ILYDQK9mLfWTh.DyYeFFOWNytpo2W7NMVP3ICW4iO6elIuKyaA0.', '스토어링크 본사', 'INSTRUCTOR', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    name = VALUES(name),
    role = VALUES(role),
    updated_at = NOW(6);

-- 상품: courses의 기존 컬럼을 상품 의미로 사용한다.
INSERT INTO courses
    (id, title, description, category, price, instructor_id, enrollment_count, status, created_at, updated_at)
VALUES
    (101, '제주 삼다수 500ml', '냉장 진열에 적합한 생수 낱병 상품', 'FRONTEND', 650.00, 102, 84, 'ACTIVE', NOW(6), NOW(6)),
    (102, '제로 콜라 355ml', '무설탕 탄산음료 캔 상품', 'FRONTEND', 980.00, 102, 72, 'ACTIVE', NOW(6), NOW(6)),
    (103, '아메리카노 컵커피', '출근 시간대 판매용 냉장 컵커피', 'FRONTEND', 1450.00, 102, 46, 'ACTIVE', NOW(6), NOW(6)),
    (104, '참치마요 삼각김밥', '간편하게 취식 가능한 냉장 삼각김밥', 'BACKEND', 1050.00, 102, 39, 'ACTIVE', NOW(6), NOW(6)),
    (105, '직화 불고기 도시락', '전자레인지 조리용 간편 도시락', 'BACKEND', 3900.00, 102, 24, 'ACTIVE', NOW(6), NOW(6)),
    (106, '감자칩 오리지널', '기본 맛 봉지 스낵', 'MOBILE', 1200.00, 102, 67, 'ACTIVE', NOW(6), NOW(6)),
    (107, '초코 크림 쿠키', '개별 포장 간식 상품', 'MOBILE', 900.00, 102, 58, 'ACTIVE', NOW(6), NOW(6)),
    (108, '프리미엄 바나나', '낱개 판매용 신선 과일', 'DATA_SCIENCE', 1100.00, 102, 31, 'ACTIVE', NOW(6), NOW(6)),
    (109, '데일리 물티슈 20매', '휴대용 위생 물티슈', 'SECURITY', 1350.00, 102, 76, 'ACTIVE', NOW(6), NOW(6)),
    (110, '여행용 칫솔 세트', '칫솔과 치약으로 구성된 휴대용 세트', 'DEVOPS', 2100.00, 102, 43, 'ACTIVE', NOW(6), NOW(6)),
    (111, '신선 우유 900ml', '냉장 보관 일반 흰 우유', 'DATABASE', 2350.00, 102, 28, 'ACTIVE', NOW(6), NOW(6)),
    (112, 'AA 건전지 4입', '생활용품 코너용 알카라인 건전지', 'OTHER', 3200.00, 102, 52, 'ACTIVE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    category = VALUES(category),
    price = VALUES(price),
    instructor_id = VALUES(instructor_id),
    enrollment_count = VALUES(enrollment_count),
    status = VALUES(status),
    updated_at = NOW(6);

-- 발주 상태별 예시 데이터
INSERT INTO enrollments (id, user_id, course_id, status, created_at, updated_at) VALUES
    (101, 101, 101, 'RECEIVED', DATE_SUB(NOW(6), INTERVAL 12 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY)),
    (102, 101, 103, 'RECEIVED', DATE_SUB(NOW(6), INTERVAL 8 DAY), DATE_SUB(NOW(6), INTERVAL 6 DAY)),
    (103, 101, 106, 'APPROVED', DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_SUB(NOW(6), INTERVAL 1 DAY)),
    (104, 101, 108, 'REQUESTED', DATE_SUB(NOW(6), INTERVAL 5 HOUR), DATE_SUB(NOW(6), INTERVAL 5 HOUR)),
    (105, 101, 110, 'REJECTED', DATE_SUB(NOW(6), INTERVAL 4 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY))
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    updated_at = VALUES(updated_at);

-- 정산 예시 데이터
INSERT INTO payments
    (id, user_id, course_id, amount, status, transaction_id, created_at, updated_at)
VALUES
    (101, 101, 101, 650.00, 'COMPLETED', 'STORELINK-DEMO-101', DATE_SUB(NOW(6), INTERVAL 11 DAY), DATE_SUB(NOW(6), INTERVAL 10 DAY)),
    (102, 101, 103, 1450.00, 'COMPLETED', 'STORELINK-DEMO-102', DATE_SUB(NOW(6), INTERVAL 7 DAY), DATE_SUB(NOW(6), INTERVAL 6 DAY))
ON DUPLICATE KEY UPDATE
    amount = VALUES(amount),
    status = VALUES(status),
    updated_at = VALUES(updated_at);
