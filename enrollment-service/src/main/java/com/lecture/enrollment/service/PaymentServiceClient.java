package com.lecture.enrollment.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

/**
 * 정산 서비스(payment-service) 호출 클라이언트
 * - payment-service는 이번 변경 대상이 아니므로 요청/응답 스펙은 기존 그대로 사용한다.
 * - 본사 발주 승인 시점에 가맹점 정산을 요청한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * 정산 요청 (동기 REST)
     *
     * @param storeId   가맹점 관리자 ID
     * @param productId 상품 ID
     * @param amount    정산 금액 (상품 공급가)
     */
    public SettlementResult requestSettlement(Long storeId, Long productId, BigDecimal amount) {
        try {
            SettlementRequest request = new SettlementRequest(storeId, productId, amount);

            SettlementResult result = webClientBuilder.build()
                    .post()
                    .uri("http://payment-service:8084/api/payments/internal/request")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SettlementResult.class)
                    .block();

            log.info("[PaymentServiceClient] 정산 요청 완료 - storeId: {}, productId: {}, amount: {}, result: {}",
                    storeId, productId, amount, result != null ? result.getStatus() : "null");

            return result;
        } catch (Exception e) {
            log.error("[PaymentServiceClient] 정산 요청 실패 - storeId: {}, productId: {}, error: {}",
                    storeId, productId, e.getMessage(), e);
            throw new RuntimeException("Settlement Service 연결 실패");
        }
    }

    /** payment-service 내부 API 요청 본문 (필드명은 기존 스펙 유지) */
    @Getter
    @NoArgsConstructor
    static class SettlementRequest {
        private Long userId;
        private Long courseId;
        private BigDecimal amount;

        SettlementRequest(Long userId, Long courseId, BigDecimal amount) {
            this.userId = userId;
            this.courseId = courseId;
            this.amount = amount;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class SettlementResult {
        private Long paymentId;
        private String status; // COMPLETED / FAILED
    }
}
