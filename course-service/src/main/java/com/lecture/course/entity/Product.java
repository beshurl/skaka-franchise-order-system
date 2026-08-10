package com.lecture.course.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 테이블명은 courses로 유지 (enrollments/payments의 FK가 이 테이블을 참조 중 -
// 이름을 바꾸려면 init-db/01_init.sql과 enrollment/payment 쪽을 함께 고쳐야 함)
// 필드명은 API 문서(팀 합의) 기준으로 원본 템플릿 이름을 그대로 유지함:
// title=상품명, price=공급가, instructorId=본사 관리자 ID, enrollmentCount=상품 관련 수량 값
@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // 본사 관리자 ID (users 테이블 참조 - 직접 JOIN 없이 ID만 보관)
    @Column(nullable = false)
    private Long instructorId;

    // 상품 관련 수량 값 (발주 확정 시 증가)
    @Column(nullable = false)
    @Builder.Default
    private Integer enrollmentCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // vue-frontend(utils/business.js categoryMeta)의 8개 코드값을 정본으로 사용.
    // 원본 템플릿 카테고리(BACKEND 등)로 요청이 와도 JsonCreator가 자동으로 매핑해준다.
    public enum Category {
        FOOD, DRINK, DAILY, FRESH, SNACK, HYGIENE, CHILLED, OTHER;

        @JsonCreator
        public static Category from(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toUpperCase();
            return switch (normalized) {
                case "FOOD", "BACKEND" -> FOOD;
                case "DRINK", "FRONTEND" -> DRINK;
                case "DAILY", "DEVOPS" -> DAILY;
                case "FRESH", "DATA_SCIENCE" -> FRESH;
                case "SNACK", "MOBILE" -> SNACK;
                case "HYGIENE", "SECURITY" -> HYGIENE;
                case "CHILLED", "DATABASE" -> CHILLED;
                default -> OTHER;
            };
        }
    }

    public enum Status {
        ACTIVE, INACTIVE
    }

    public void increaseEnrollmentCount() {
        this.enrollmentCount++;
    }

    public void update(String title, String description, Category category,
                        BigDecimal price, Status status) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (price != null) this.price = price;
        if (status != null) this.status = status;
    }
}
