package com.lecture.enrollment.controller;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 발주 API
 * - Base path 는 기존 스켈레톤의 /api/enrollments 를 유지한다.
 * - Gateway 가 전달하는 X-User-Id(사용자 ID), X-User-Role(권한) 헤더를 사용한다.
 *
 * 가맹점(STORE_ADMIN)
 *   POST   /api/enrollments                     발주 요청
 *   GET    /api/enrollments/my                  내 발주 목록
 *   GET    /api/enrollments/{orderId}           발주 상세
 *   PATCH  /api/enrollments/{orderId}/receive   입고 확인
 *
 * 본사(HEADQUARTERS_ADMIN)
 *   GET    /api/enrollments/admin                       전체 발주 목록
 *   PATCH  /api/enrollments/admin/{orderId}/approve     발주 승인
 *   PATCH  /api/enrollments/admin/{orderId}/reject      발주 반려
 *
 * 내부 호출
 *   GET    /api/enrollments/internal/store/{storeId}/received  입고 완료 상품 ID 목록
 */
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    // Gateway가 X-User-Role에 넣어 보내는 값은 DomainRole이 아니라 JWT의 원본 role 클레임
    // (auth-server가 발급하는 값은 User.Role의 STUDENT/INSTRUCTOR 그대로임) 이라 그 값과 비교해야 한다.
    private static final String ROLE_HEADQUARTERS = "INSTRUCTOR";
    private static final String ROLE_STORE = "STUDENT";

    private final EnrollmentService enrollmentService;

    /**
     * POST /api/enrollments - 발주 요청 (가맹점)
     */
    @PostMapping
    public ResponseEntity<EnrollmentDto.ApiResponse<EnrollmentDto.OrderResponse>> createOrder(
            @Valid @RequestBody EnrollmentDto.OrderRequest request,
            @RequestHeader("X-User-Id") Long storeId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireRole(role, ROLE_STORE);

        EnrollmentDto.OrderResponse response = enrollmentService.createOrder(storeId, request.getCourseId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EnrollmentDto.ApiResponse.success("발주가 접수되었습니다", response));
    }

    /**
     * GET /api/enrollments/my - 내 발주 목록 (가맹점)
     */
    @GetMapping("/my")
    public ResponseEntity<EnrollmentDto.ApiResponse<List<EnrollmentDto.OrderResponse>>> getMyOrders(
            @RequestHeader("X-User-Id") Long storeId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) Enrollment.Status status) {

        requireRole(role, ROLE_STORE);

        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success(enrollmentService.getStoreOrders(storeId, status)));
    }

    /**
     * GET /api/enrollments/{orderId} - 발주 상세
     * 가맹점은 본인 발주만, 본사는 전체 조회 가능
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<EnrollmentDto.ApiResponse<EnrollmentDto.OrderResponse>> getOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        boolean headquarters = ROLE_HEADQUARTERS.equals(role);

        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success(
                        enrollmentService.getOrder(orderId, requesterId, headquarters)));
    }

    /**
     * PATCH /api/enrollments/{orderId}/receive - 입고 확인 (가맹점)
     * APPROVED -> RECEIVED 전이 후 상품 재고를 증가시킨다.
     */
    @RequestMapping(
            path = "/{orderId}/receive",
            method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<EnrollmentDto.ApiResponse<EnrollmentDto.OrderStatusResponse>> receiveOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long storeId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireRole(role, ROLE_STORE);

        EnrollmentDto.OrderStatusResponse response = enrollmentService.receiveOrder(orderId, storeId);
        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success("입고가 완료되고 재고가 반영되었습니다", response));
    }

    /**
     * GET /api/enrollments/admin - 전체 발주 목록 (본사)
     */
    @GetMapping("/admin")
    public ResponseEntity<EnrollmentDto.ApiResponse<List<EnrollmentDto.OrderResponse>>> getAllOrders(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) Enrollment.Status status,
            @RequestParam(required = false) Long storeId) {

        requireRole(role, ROLE_HEADQUARTERS);

        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success(enrollmentService.getAllOrders(status, storeId)));
    }

    /**
     * PATCH /api/enrollments/admin/{orderId}/approve - 발주 승인 (본사)
     */
    @RequestMapping(
            path = "/admin/{orderId}/approve",
            method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<EnrollmentDto.ApiResponse<EnrollmentDto.OrderStatusResponse>> approveOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireRole(role, ROLE_HEADQUARTERS);

        EnrollmentDto.OrderStatusResponse response = enrollmentService.approveOrder(orderId);
        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success("발주가 승인되었습니다", response));
    }

    /**
     * PATCH /api/enrollments/admin/{orderId}/reject - 발주 반려 (본사)
     */
    @RequestMapping(
            path = "/admin/{orderId}/reject",
            method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<EnrollmentDto.ApiResponse<EnrollmentDto.OrderStatusResponse>> rejectOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody EnrollmentDto.RejectRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireRole(role, ROLE_HEADQUARTERS);

        EnrollmentDto.OrderStatusResponse response =
                enrollmentService.rejectOrder(orderId, request.getReason());
        return ResponseEntity.ok(
                EnrollmentDto.ApiResponse.success("발주가 반려되었습니다", response));
    }

    /**
     * GET /api/enrollments/internal/store/{storeId}/received - 입고 완료 상품 ID 목록 (내부 호출)
     */
    @GetMapping("/internal/store/{storeId}/received")
    public ResponseEntity<EnrollmentDto.StoreOrderHistoryResponse> getReceivedProducts(
            @PathVariable Long storeId) {

        return ResponseEntity.ok(enrollmentService.getReceivedProductIds(storeId));
    }

    /**
     * Gateway 가 전달한 역할 헤더 검증
     */
    private void requireRole(String actualRole, String requiredRole) {
        String normalizedRole = switch (actualRole == null ? "" : actualRole) {
            case "STUDENT" -> ROLE_STORE;
            case "INSTRUCTOR" -> ROLE_HEADQUARTERS;
            default -> actualRole;
        };

        if (!requiredRole.equals(normalizedRole)) {
            throw new SecurityException(requiredRole + " 권한이 필요합니다");
        }
    }
}
