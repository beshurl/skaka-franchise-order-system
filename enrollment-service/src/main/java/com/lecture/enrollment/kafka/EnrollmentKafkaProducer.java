package com.lecture.enrollment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.enrollment-completed}")
    private String orderReceivedTopic;

    /**
     * 입고 완료 이벤트 발행
     * - 발주가 RECEIVED 로 전이되고 재고가 반영된 뒤 발행한다.
     */
    public void publishOrderReceived(KafkaEvent.OrderReceivedEvent event) {
        log.info("[Kafka Producer] 입고 완료 이벤트 발행 - orderId: {}, storeId: {}, productId: {}",
                event.getEnrollmentId(), event.getUserId(), event.getCourseId());

        kafkaTemplate.send(orderReceivedTopic, String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka Producer] 입고 완료 이벤트 발행 실패: {}", ex.getMessage());
                    } else {
                        log.info("[Kafka Producer] 입고 완료 이벤트 발행 성공 - offset: {}",
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
