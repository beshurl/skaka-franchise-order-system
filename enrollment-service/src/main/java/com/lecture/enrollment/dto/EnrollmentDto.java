package com.lecture.enrollment.dto;

import com.lecture.enrollment.entity.Enrollment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 발주 서비스 DTO
 * - userId   : 가맹점 관리자 ID
 * - courseId : 상품 ID
 *   (DB 컬럼명을 유지하기 위해 필드명도 그대로 사용한다)
 */
public class EnrollmentDto {

    // 발주 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderRequest {
        @NotNull(message = "상품 ID는 필수입니다")
        private Long courseId;

        /** 기존 클라이언트가 생략하면 1개로 처리한다. */
        @Min(value = 1, message = "발주 수량은 1개 이상이어야 합니다")
        @Max(value = 999, message = "발주 수량은 999개 이하여야 합니다")
        @Builder.Default
        private Integer quantity = 1;
    }

    // 발주 반려 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RejectRequest {
        @NotBlank(message = "반려 사유는 필수입니다")
        private String reason;
    }

    // 상품 요약 정보 (발주 목록 표시용, product-service(course-service)에서 조회)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductSummary {
        private Long id;
        private String name;          // courses.title
        private String description;
        private String category;
        private BigDecimal supplyPrice; // courses.price (공급가)
        private Integer stockQuantity;  // courses.enrollment_count (가맹점 재고 수량)
    }

    // 발주 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderResponse {
        private Long id;
        private Long userId;    // 가맹점 관리자 ID
        private Long courseId;  // 상품 ID
        private Integer quantity; // 발주 수량
        private Enrollment.Status status;
        private String rejectReason; // REJECTED 상태일 때만 값이 있음
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // 상품 상세를 함께 내려줄 때만 채워진다
        private ProductSummary product;

        public static OrderResponse from(Enrollment order) {
            return from(order, null);
        }

        public static OrderResponse from(Enrollment order, ProductSummary product) {
            return OrderResponse.builder()
                    .id(order.getId())
                    .userId(order.getUserId())
                    .courseId(order.getCourseId())
                    .quantity(order.getQuantity())
                    .status(order.getStatus())
                    .rejectReason(order.getRejectReason())
                    .createdAt(order.getCreatedAt())
                    .updatedAt(order.getUpdatedAt())
                    .product(product)
                    .build();
        }
    }

    // 상태 전이 결과 응답 (승인/반려/입고)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderStatusResponse {
        private Long orderId;
        private Long userId;
        private Long courseId;
        private Integer quantity;
        private Enrollment.Status status;
        private String rejectReason; // REJECTED 상태일 때만 값이 있음
        private LocalDateTime updatedAt;

        public static OrderStatusResponse from(Enrollment order) {
            return OrderStatusResponse.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .courseId(order.getCourseId())
                    .quantity(order.getQuantity())
                    .status(order.getStatus())
                    .rejectReason(order.getRejectReason())
                    .updatedAt(order.getUpdatedAt())
                    .build();
        }
    }

    // 내부 API: 가맹점이 입고 완료한 상품 ID 목록
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StoreOrderHistoryResponse {
        private Long userId;
        private List<Long> receivedProductIds;
    }

    // 공통 API 응답 래퍼
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return success("성공", data);
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
