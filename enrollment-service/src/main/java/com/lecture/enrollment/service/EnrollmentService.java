package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.kafka.KafkaEvent;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 발주 서비스
 *
 * 상태 흐름
 *   REQUESTED -> APPROVED -> RECEIVED
 *        |
 *        +---> REJECTED
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private static final List<Enrollment.Status> IN_PROGRESS_STATUSES = List.of(
            Enrollment.Status.REQUESTED,
            Enrollment.Status.APPROVED
    );

    private final EnrollmentRepository enrollmentRepository;
    private final CourseServiceClient courseServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final EnrollmentKafkaProducer kafkaProducer;
    private final EnrollmentWriteService enrollmentWriteService;

    /**
     * 발주 요청 (가맹점)
     * 1. 상품 존재 확인
     * 2. 동일 상품에 처리 중인 발주가 있는지 확인
     * 3. REQUESTED 상태로 저장 (독립 트랜잭션 커밋)
     */
    public EnrollmentDto.OrderResponse createOrder(Long storeId, Long productId, Integer requestedQuantity) {
        int quantity = requestedQuantity == null ? 1 : requestedQuantity;
        validateQuantity(quantity);

        if (!courseServiceClient.existsProduct(productId)) {
            throw new IllegalArgumentException("존재하지 않는 상품입니다: " + productId);
        }

        if (enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                storeId,
                productId,
                IN_PROGRESS_STATUSES
        )) {
            throw new IllegalStateException("이미 처리 중인 발주가 있는 상품입니다: " + productId);
        }

        Enrollment order = enrollmentWriteService.createRequestedOrder(storeId, productId, quantity);

        log.info("[EnrollmentService] 발주 요청 접수 - orderId: {}, quantity: {}, 상태: {}",
                order.getId(), quantity, order.getStatus());
        return EnrollmentDto.OrderResponse.from(order);
    }

    /**
     * 발주 승인 (본사)
     * 1. REQUESTED -> APPROVED 전이 후 즉시 커밋
     * 2. 상품 공급가로 정산 요청 (실패해도 승인은 유지하고 로그만 남김)
     */
    public EnrollmentDto.OrderStatusResponse approveOrder(Long orderId) {
        Enrollment order = enrollmentWriteService.approveOrder(orderId);

        BigDecimal supplyPrice = getSupplyPrice(order.getCourseId());
        BigDecimal settlementAmount = supplyPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        try {
            paymentServiceClient.requestSettlement(order.getUserId(), order.getCourseId(), settlementAmount);
        } catch (RuntimeException e) {
            log.error("[EnrollmentService] 정산 요청 실패 - orderId: {}, error: {}", orderId, e.getMessage());
        }

        log.info("[EnrollmentService] 발주 승인 처리 완료 - orderId: {}, quantity: {}, 정산금액: {}",
                orderId, order.getQuantity(), settlementAmount);
        return EnrollmentDto.OrderStatusResponse.from(order);
    }

    /**
     * 발주 반려 (본사)
     */
    public EnrollmentDto.OrderStatusResponse rejectOrder(Long orderId, String reason) {
        Enrollment order = enrollmentWriteService.rejectOrder(orderId, reason);

        log.info("[EnrollmentService] 발주 반려 처리 완료 - orderId: {}", orderId);
        return EnrollmentDto.OrderStatusResponse.from(order);
    }

    /**
     * 입고 확인 (가맹점)
     * 1. 본인 발주 여부와 APPROVED 상태 검증 후 RECEIVED 로 전이하고 커밋 (중복 입고 방지)
     * 2. 상품 재고 수량 증가
     * 3. 입고 완료 이벤트 발행
     */
    public EnrollmentDto.OrderStatusResponse receiveOrder(Long orderId, Long storeId) {
        Enrollment order = enrollmentWriteService.receiveOrder(orderId, storeId);

        courseServiceClient.increaseStock(order.getCourseId(), order.getQuantity());

        kafkaProducer.publishOrderReceived(
                KafkaEvent.OrderReceivedEvent.builder()
                        .enrollmentId(order.getId())
                        .userId(order.getUserId())
                        .courseId(order.getCourseId())
                        .quantity(order.getQuantity())
                        .build()
        );

        log.info("[EnrollmentService] 입고 처리 완료 - orderId: {}", orderId);
        return EnrollmentDto.OrderStatusResponse.from(order);
    }

    /**
     * 정산 완료 확인 (payment.completed 이벤트 수신)
     * - enrollments 테이블에 정산 상태 컬럼이 없어 상태 정합성 검증과 로깅만 수행한다.
     */
    public void confirmSettlement(Long storeId, Long productId) {
        Enrollment order = enrollmentRepository.findFirstByUserIdAndCourseIdOrderByIdDesc(storeId, productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "발주 정보를 찾을 수 없습니다 - storeId: " + storeId + ", productId: " + productId));

        if (order.getStatus() != Enrollment.Status.APPROVED) {
            log.warn("[EnrollmentService] 정산 완료 이벤트와 최신 발주 상태 불일치 - orderId: {}, 상태: {}",
                    order.getId(), order.getStatus());
            return;
        }

        log.info("[EnrollmentService] 정산 완료 확인 - orderId: {}, storeId: {}, productId: {}",
                order.getId(), storeId, productId);
    }

    /**
     * 가맹점 발주 목록 조회 (상품 정보 포함)
     */
    public List<EnrollmentDto.OrderResponse> getStoreOrders(Long storeId, Enrollment.Status status) {
        List<Enrollment> orders = (status == null)
                ? enrollmentRepository.findByUserIdOrderByIdDesc(storeId)
                : enrollmentRepository.findByUserIdAndStatusOrderByIdDesc(storeId, status);

        return toResponsesWithProduct(orders);
    }

    /**
     * 본사 전체 발주 목록 조회 (상태/가맹점 필터)
     */
    public List<EnrollmentDto.OrderResponse> getAllOrders(Enrollment.Status status, Long storeId) {
        List<Enrollment> orders = (status == null)
                ? enrollmentRepository.findAllByOrderByIdDesc()
                : enrollmentRepository.findByStatusOrderByIdDesc(status);

        if (storeId != null) {
            orders = orders.stream()
                    .filter(order -> order.isOwnedBy(storeId))
                    .collect(Collectors.toList());
        }

        return toResponsesWithProduct(orders);
    }

    /**
     * 발주 상세 조회
     * - 본사는 전체 조회, 가맹점은 본인 발주만 조회 가능
     */
    public EnrollmentDto.OrderResponse getOrder(Long orderId, Long requesterId, boolean headquarters) {
        Enrollment order = enrollmentRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 발주입니다: " + orderId));

        if (!headquarters && !order.isOwnedBy(requesterId)) {
            throw new SecurityException("본인 가맹점의 발주만 조회할 수 있습니다");
        }

        return EnrollmentDto.OrderResponse.from(order, toProductSummary(order.getCourseId()));
    }

    /**
     * 내부 API: 가맹점이 입고 완료(RECEIVED)한 상품 ID 목록
     */
    public EnrollmentDto.StoreOrderHistoryResponse getReceivedProductIds(Long storeId) {
        List<Long> receivedProductIds = enrollmentRepository
                .findByUserIdAndStatus(storeId, Enrollment.Status.RECEIVED)
                .stream()
                .map(Enrollment::getCourseId)
                .collect(Collectors.toList());

        return EnrollmentDto.StoreOrderHistoryResponse.builder()
                .userId(storeId)
                .receivedProductIds(receivedProductIds)
                .build();
    }

    // ---------------------------------------------------------------- private

    private List<EnrollmentDto.OrderResponse> toResponsesWithProduct(List<Enrollment> orders) {
        return orders.stream()
                .map(order -> EnrollmentDto.OrderResponse.from(order, toProductSummary(order.getCourseId())))
                .collect(Collectors.toList());
    }

    /**
     * 상품 정보 조립
     * - 상품 서비스 장애 시 발주 목록 자체가 실패하지 않도록 null 로 대체한다.
     */
    private EnrollmentDto.ProductSummary toProductSummary(Long productId) {
        try {
            Map<String, Object> product = courseServiceClient.getProduct(productId);

            return EnrollmentDto.ProductSummary.builder()
                    .id(toLong(product.get("id")))
                    .name((String) product.get("title"))
                    .description((String) product.get("description"))
                    .category(normalizeCategory((String) product.get("category")))
                    .supplyPrice(toBigDecimal(product.get("price")))
                    .stockQuantity(toInteger(
                            firstNonNullObject(
                                    product.get("enrollmentCount"),
                                    product.get("enrollment_count")
                            )
                    ))
                    .build();
        } catch (RuntimeException e) {
            log.warn("[EnrollmentService] 상품 정보 조회 실패로 상품 정보 없이 응답 - productId: {}, error: {}",
                    productId, e.getMessage());
            return null;
        }
    }

    private BigDecimal getSupplyPrice(Long productId) {
        Map<String, Object> product = courseServiceClient.getProduct(productId);
        BigDecimal supplyPrice = toBigDecimal(product.get("price"));

        if (supplyPrice == null) {
            throw new IllegalStateException("상품 공급가를 확인할 수 없습니다: " + productId);
        }
        return supplyPrice;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 999) {
            throw new IllegalArgumentException("발주 수량은 1개 이상 999개 이하여야 합니다");
        }
    }

    private String normalizeCategory(String category) {
        if (category == null) return null;

        return switch (category) {
            case "DRINK" -> "음료";
            case "FOOD" -> "식품";
            case "DAILY" -> "생활용품";
            case "OTHER" -> "기타";
            default -> category;
        };
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(value.toString());
    }

    private Object firstNonNullObject(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
