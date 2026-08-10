package com.lecture.enrollment.kafka;

import com.lecture.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentKafkaConsumer {

    private final EnrollmentService enrollmentService;

    /**
     * 정산 완료(payment.completed) 이벤트 수신
     * → 승인된 발주의 정산 완료 여부를 확인한다.
     *
     * payment-service 는 JsonSerializer + type header 미포함으로 이벤트를 발행하므로,
     * 특정 DTO 타입으로 바로 받지 않고 Map<String, Object> 로 받아 처리한다.
     */
    @KafkaListener(
            topics = "${kafka.topic.payment-completed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSettlementCompleted(Map<String, Object> event) {
        log.info("[Kafka Consumer] 정산 완료 raw event 수신: {}", event);

        try {
            Object storeIdValue = event.get("userId");
            Object productIdValue = event.get("courseId");

            if (storeIdValue == null || productIdValue == null) {
                throw new IllegalArgumentException("Kafka 이벤트에 userId 또는 courseId가 없습니다.");
            }

            Long storeId = ((Number) storeIdValue).longValue();
            Long productId = ((Number) productIdValue).longValue();

            enrollmentService.confirmSettlement(storeId, productId);

        } catch (Exception e) {
            log.error("[Kafka Consumer] 정산 완료 처리 실패 - event: {}, error: {}",
                    event, e.getMessage(), e);
        }
    }
}
