package com.lecture.payment.controller;

import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 정산(결제) API
 * - Gateway 가 전달하는 X-User-Id(사용자 ID), X-User-Role(권한) 헤더를 사용한다
 *   (course-service/enrollment-service와 동일한 컨벤션, PaymentController.requireXxx 참고).
 * - /internal/** 은 다른 서비스가 컨테이너 네트워크로 직접 호출하는 내부 API라
 *   Gateway를 거치지 않으므로 위 헤더가 없다. 그래서 헤더를 요구하지 않는다.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    // Gateway가 X-User-Role에 넣어 보내는 값은 JWT의 원본 role 클레임 그대로다
    // (STUDENT/INSTRUCTOR, DomainRole 아님) - enrollment-service/course-service와 동일.
    private static final String ROLE_HEADQUARTERS = "INSTRUCTOR";
    private static final String ROLE_STORE = "STUDENT";

    private final PaymentService paymentService;

    /**
     * POST /payments/internal/request - 내부 결제 요청 (Enrollment Service 호출)
     */
    @PostMapping("/internal/request")
    public ResponseEntity<PaymentDto.InternalPaymentResult> processInternalPayment(
            @RequestBody PaymentDto.InternalPaymentRequest request) {

        PaymentDto.InternalPaymentResult result = paymentService.processInternalPayment(request);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /payments/{id} - 결제 단건 조회
     * 본사는 전체, 가맹점은 본인 소유 결제만 조회 가능
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto.ApiResponse<PaymentDto.PaymentResponse>> getPayment(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        PaymentDto.PaymentResponse payment = paymentService.getPayment(id);
        requireOwnerOrHeadquarters(payment.getUserId(), requesterId, role);

        return ResponseEntity.ok(PaymentDto.ApiResponse.success(payment));
    }

    /**
     * GET /payments/user/{userId} - 사용자 결제 내역 조회
     * 본사는 아무 가맹점이나, 가맹점은 본인 결제 내역만 조회 가능
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<PaymentDto.ApiResponse<List<PaymentDto.PaymentResponse>>> getPaymentsByUser(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireOwnerOrHeadquarters(userId, requesterId, role);

        return ResponseEntity.ok(
                PaymentDto.ApiResponse.success(paymentService.getPaymentsByUser(userId)));
    }

    /**
     * GET /payments/admin - 전체 정산 내역 (본사)
     */
    @GetMapping("/admin")
    public ResponseEntity<PaymentDto.ApiResponse<List<PaymentDto.PaymentResponse>>> getAllPayments(
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireRole(role, ROLE_HEADQUARTERS);

        return ResponseEntity.ok(
                PaymentDto.ApiResponse.success(paymentService.getAllPayments()));
    }

    private void requireRole(String actualRole, String requiredRole) {
        if (!requiredRole.equals(actualRole)) {
            throw new SecurityException(requiredRole + " 권한이 필요합니다");
        }
    }

    /** 본사는 통과, 가맹점은 본인 소유일 때만 통과 */
    private void requireOwnerOrHeadquarters(Long ownerId, Long requesterId, String role) {
        if (ROLE_HEADQUARTERS.equals(role)) {
            return;
        }
        if (ROLE_STORE.equals(role) && ownerId.equals(requesterId)) {
            return;
        }
        throw new SecurityException("본인 소유 정산 내역만 조회할 수 있습니다");
    }
}
