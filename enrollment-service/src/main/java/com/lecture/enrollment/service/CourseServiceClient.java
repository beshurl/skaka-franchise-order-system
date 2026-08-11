package com.lecture.enrollment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 상품 서비스(course-service) 호출 클라이언트
 * - course-service는 이번 변경 대상이 아니므로 URL과 응답 필드명은 기존 그대로 사용한다.
 *   courses.title            -> 상품명
 *   courses.price            -> 공급가
 *   courses.enrollment_count -> 가맹점 재고 수량
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseServiceClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * 상품 존재 여부 확인 (발주 생성 전 검증)
     */
    public boolean existsProduct(Long productId) {
        try {
            Boolean exists = webClientBuilder.build()
                    .get()
                    .uri("http://course-service/api/courses/internal/exists/{id}", productId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("[CourseServiceClient] 상품 존재 확인 실패 - productId: {}, error: {}",
                    productId, e.getMessage());
            throw new RuntimeException("Product Service 연결 실패");
        }
    }

    /**
     * 상품 상세 조회
     * - 발주 목록/상세 응답에 상품 정보를 붙일 때 사용
     * - 정산 금액(공급가) 계산에도 사용
     */
    public Map<String, Object> getProduct(Long productId) {
        try {
            Map<String, Object> responseBody = webClientBuilder.build()
                    .get()
                    .uri("http://course-service/api/courses/internal/{id}", productId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (responseBody == null) {
                throw new RuntimeException("Product Service 응답 본문이 비어 있습니다.");
            }

            log.debug("[CourseServiceClient] 상품 상세 조회 성공 - productId: {}, body: {}", productId, responseBody);

            /*
             * 응답이 공통 래퍼({success, message, data})로 오는 경우와
             * 상품 객체가 그대로 오는 경우를 모두 처리한다.
             */
            Object data = responseBody.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> productMap = (Map<String, Object>) dataMap;
                return productMap;
            }

            return responseBody;
        } catch (Exception e) {
            log.error("[CourseServiceClient] 상품 상세 조회 실패 - productId: {}, error: {}",
                    productId, e.getMessage());
            throw new RuntimeException("Product Service 상품 상세 조회 실패");
        }
    }

    /**
     * 가맹점 재고 수량 증가 (입고 확인 시 호출)
     * - course-service의 기존 enrollment-count 증가 엔드포인트를 재고 증가 용도로 사용한다.
     */
    public void increaseStock(Long productId, int quantity) {
        try {
            webClientBuilder.build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("course-service")
                            .path("/api/courses/internal/{id}/enrollment-count")
                            .queryParam("quantity", quantity)
                            .build(productId))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("[CourseServiceClient] 재고 수량 증가 완료 - productId: {}, quantity: {}",
                    productId, quantity);
        } catch (Exception e) {
            log.error("[CourseServiceClient] 재고 수량 증가 실패 - productId: {}, quantity: {}, error: {}",
                    productId, quantity, e.getMessage());
        }
    }
}
