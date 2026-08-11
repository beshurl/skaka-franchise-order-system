package com.lecture.enrollment.kafka;

import lombok.*;

/**
 * Kafka 이벤트 메시지 DTO
 */
public class KafkaEvent {

    /**
     * Payment(정산) Service -> Enrollment(발주) Service
     * 정산 완료 이벤트 수신
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettlementCompletedEvent {
        private Long paymentId;
        private Long userId;   // 가맹점 관리자 ID
        private Long courseId; // 상품 ID
        private String status; // COMPLETED
    }

    /**
     * Enrollment(발주) Service -> 구독 서비스
     * 입고 완료 이벤트 발행
     *
     * 필드명은 기존 스켈레톤의 이벤트 스키마를 유지한다.
     *   enrollmentId : 발주 ID
     *   userId       : 가맹점 관리자 ID
     *   courseId     : 상품 ID
     *   quantity     : 입고 수량
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderReceivedEvent {
        private Long enrollmentId;
        private Long userId;
        private Long courseId;
        private Integer quantity;
    }
}
