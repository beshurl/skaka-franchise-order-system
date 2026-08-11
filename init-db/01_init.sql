-- B2B 편의점 본사-가맹점 발주 관리 시스템 초기 DDL
--
-- 기존 테이블과 컬럼을 그대로 사용하고 의미만 변경한다.
-- users       : 사용자 및 본사/가맹점 권한
-- courses     : 상품 및 상품 재고 수량
-- enrollments : 발주
-- payments    : 정산
--
-- 새 테이블과 새 컬럼은 추가하지 않는다.

-- 사용자
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(30)     NOT NULL
                COMMENT 'STUDENT(가맹점) | INSTRUCTOR(본사)',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 상품
-- 기존 courses 테이블을 상품 테이블로 사용한다.
-- title            : 상품명
-- price            : 공급가
-- instructor_id    : 상품을 등록한 본사 관리자 ID
-- enrollment_count : 가맹점 재고 수량
CREATE TABLE IF NOT EXISTS courses (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    title            VARCHAR(255)    NOT NULL,
    description      TEXT,
    category         VARCHAR(50)     NOT NULL
                     COMMENT 'FOOD | DRINK | DAILY | FRESH | SNACK | HYGIENE | CHILLED | OTHER',
    price            DECIMAL(10,2)   NOT NULL,
    instructor_id    BIGINT          NOT NULL,
    enrollment_count INT             NOT NULL DEFAULT 0,
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                     COMMENT 'ACTIVE | INACTIVE',
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (instructor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 발주
-- 기존 enrollments 테이블을 발주 테이블로 사용한다.
-- user_id   : 가맹점 관리자 ID
-- course_id : 상품 ID
CREATE TABLE IF NOT EXISTS enrollments (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    course_id   BIGINT          NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'REQUESTED'
                COMMENT 'REQUESTED | APPROVED | REJECTED | RECEIVED',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_store_product_order (user_id, course_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 정산
-- 기존 payments 테이블을 정산 테이블로 사용한다.
-- user_id        : 가맹점 관리자 ID
-- course_id      : 상품 ID
-- amount         : 정산 금액
-- transaction_id : 정산 거래 ID
CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    course_id       BIGINT          NOT NULL,
    amount          DECIMAL(10,2)   NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                    COMMENT 'PENDING | COMPLETED | FAILED | CANCELLED',
    transaction_id  VARCHAR(255)    UNIQUE,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
