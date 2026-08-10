package com.lecture.enrollment.service;

import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발주 상태 변경 전용 서비스
 * - 외부 서비스(정산/재고) 호출보다 먼저 상태 변경을 독립 트랜잭션으로 커밋한다.
 *   외부 호출 실패가 발주 상태 변경을 롤백시키지 않도록 하기 위함이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentWriteService {

    private final EnrollmentRepository enrollmentRepository;

    /**
     * 발주 요청 생성 (REQUESTED) - 독립 트랜잭션
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment createRequestedOrder(Long storeId, Long productId) {

        Enrollment order = enrollmentRepository.save(
                Enrollment.builder()
                        .userId(storeId)
                        .courseId(productId)
                        .build()
        );

        log.info("[EnrollmentWriteService] 발주 요청 생성 완료 - orderId: {}, storeId: {}, productId: {}",
                order.getId(), storeId, productId);

        return order;
    }

    /**
     * 본사 발주 승인 (REQUESTED -> APPROVED) - 독립 트랜잭션
     * 정산 요청은 이 트랜잭션이 커밋된 뒤에 수행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment approveOrder(Long orderId) {
        Enrollment order = findOrThrow(orderId);
        order.approve();

        log.info("[EnrollmentWriteService] 발주 승인 완료 - orderId: {}", orderId);
        return order;
    }

    /**
     * 본사 발주 반려 (REQUESTED -> REJECTED)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment rejectOrder(Long orderId, String reason) {
        Enrollment order = findOrThrow(orderId);
        order.reject();

        // enrollments 테이블에 반려 사유 컬럼이 없어 사유는 로그로만 남긴다.
        log.info("[EnrollmentWriteService] 발주 반려 완료 - orderId: {}, reason: {}", orderId, reason);
        return order;
    }

    /**
     * 가맹점 입고 확인 (APPROVED -> RECEIVED) - 독립 트랜잭션
     * 재고 증가와 이벤트 발행은 이 트랜잭션이 커밋된 뒤에 수행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment receiveOrder(Long orderId, Long storeId) {
        Enrollment order = findOrThrow(orderId);

        if (!order.isOwnedBy(storeId)) {
            throw new SecurityException("본인 가맹점의 발주만 입고 처리할 수 있습니다");
        }

        order.receive();

        log.info("[EnrollmentWriteService] 입고 확인 완료 - orderId: {}, storeId: {}", orderId, storeId);
        return order;
    }

    private Enrollment findOrThrow(Long orderId) {
        return enrollmentRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 발주입니다: " + orderId));
    }
}
