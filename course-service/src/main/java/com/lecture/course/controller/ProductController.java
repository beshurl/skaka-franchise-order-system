package com.lecture.course.controller;

import com.lecture.course.dto.ProductDto;
import com.lecture.course.entity.Product;
import com.lecture.course.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 경로는 /api/courses로 고정 (API Gateway가 아는 라우트가 이 이름뿐이라 변경 불가 - course-service 그대로 유지)
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class ProductController {

    // Gateway가 X-User-Role에 넣어 보내는 값은 DomainRole이 아니라 JWT의 원본 role 클레임
    // (auth-server가 발급하는 값은 User.Role의 STUDENT/INSTRUCTOR 그대로임) 이라 그 값과 비교해야 한다.
    private static final String ROLE_HEADQUARTERS = "INSTRUCTOR";

    private final ProductService productService;

    /**
     * POST /api/courses - 상품 등록 (본사 관리자만)
     * Gateway에서 전달한 X-User-Id 헤더로 본사 관리자 ID 추출
     */
    @PostMapping
    public ResponseEntity<ProductDto.ApiResponse<ProductDto.ProductResponse>> createProduct(
            @Valid @RequestBody ProductDto.CreateRequest request,
            @RequestHeader("X-User-Id") Long instructorId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireRole(role, ROLE_HEADQUARTERS);

        ProductDto.ProductResponse response = productService.createProduct(request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductDto.ApiResponse.success(response));
    }

    /**
     * GET /api/courses - 판매중 상품 전체 목록
     */
    @GetMapping
    public ResponseEntity<ProductDto.ApiResponse<List<ProductDto.ProductResponse>>> getAllProducts() {
        return ResponseEntity.ok(
                ProductDto.ApiResponse.success(productService.getAllProducts())
        );
    }

    /**
     * GET /api/courses/{id} - 상품 상세
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto.ApiResponse<ProductDto.ProductResponse>> getProduct(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ProductDto.ApiResponse.success(productService.getProduct(id))
        );
    }

    /**
     * GET /api/courses/category/{category} - 카테고리별 상품
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ProductDto.ApiResponse<List<ProductDto.ProductResponse>>> getProductsByCategory(
            @PathVariable Product.Category category) {
        return ResponseEntity.ok(
                ProductDto.ApiResponse.success(productService.getProductsByCategory(category))
        );
    }

    /**
     * PUT/PATCH /api/courses/{id} - 상품 수정 (본사 관리자만)
     * vue-frontend(course.js)가 PATCH로 호출하고 API 문서는 PUT으로 되어 있어 둘 다 받는다.
     */
    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<ProductDto.ApiResponse<ProductDto.ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductDto.UpdateRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        requireRole(role, ROLE_HEADQUARTERS);

        return ResponseEntity.ok(
                ProductDto.ApiResponse.success(productService.updateProduct(id, request))
        );
    }

    /**
     * GET /api/courses/internal/exists/{id} - 상품 존재 여부 (Enrollment Service 호출)
     */
    @GetMapping("/internal/exists/{id}")
    public ResponseEntity<Boolean> existsProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.existsProduct(id));
    }

    /**
     * GET /api/courses/internal/{id} - 상품 상세 조회 (Enrollment Service 내부 호출용)
     */
    @GetMapping("/internal/{id}")
    public ResponseEntity<ProductDto.ProductResponse> getProductInternal(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    /**
     * POST /api/courses/internal/{id}/enrollment-count - 상품 관련 수량 값 증가
     * (Enrollment Service가 발주 확정 시 호출 - URL은 기존 템플릿 계약 그대로 유지)
     */
    @PostMapping("/internal/{id}/enrollment-count")
    public ResponseEntity<Void> increaseEnrollmentCount(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int quantity) {
        productService.increaseEnrollmentCount(id, quantity);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/courses/internal/recommend - 추천 서비스용 조회
     */
    @GetMapping("/internal/recommend")
    public ResponseEntity<List<ProductDto.ProductResponse>> getRecommendProducts(
            @RequestParam Product.Category category,
            @RequestParam(defaultValue = "") List<Long> excludeIds) {
        return ResponseEntity.ok(productService.getRecommendProducts(category, excludeIds));
    }

    /**
     * Gateway가 전달한 역할 헤더 검증 (enrollment-service의 requireRole과 동일한 방식)
     */
    private void requireRole(String actualRole, String requiredRole) {
        if (!requiredRole.equals(actualRole)) {
            throw new SecurityException(requiredRole + " 권한이 필요합니다");
        }
    }
}
