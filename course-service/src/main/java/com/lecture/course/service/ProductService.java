package com.lecture.course.service;

import com.lecture.course.dto.ProductDto;
import com.lecture.course.entity.Product;
import com.lecture.course.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품 등록 (본사 관리자만 가능 - SecurityConfig에서 role 검증)
     */
    @Transactional
    public ProductDto.ProductResponse createProduct(ProductDto.CreateRequest request, Long instructorId) {
        Product product = Product.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .price(request.getPrice())
                .instructorId(instructorId)
                .build();

        return ProductDto.ProductResponse.from(productRepository.save(product));
    }

    /**
     * 상품 단건 조회
     */
    public ProductDto.ProductResponse getProduct(Long id) {
        return ProductDto.ProductResponse.from(findProductById(id));
    }

    /**
     * 판매중 상품 전체 목록 조회
     */
    public List<ProductDto.ProductResponse> getAllProducts() {
        return productRepository.findByStatus(Product.Status.ACTIVE).stream()
                .map(ProductDto.ProductResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 상품 조회
     */
    public List<ProductDto.ProductResponse> getProductsByCategory(Product.Category category) {
        return productRepository.findByCategoryAndStatus(category, Product.Status.ACTIVE).stream()
                .map(ProductDto.ProductResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 상품 수정 (본사 관리자만)
     */
    @Transactional
    public ProductDto.ProductResponse updateProduct(Long id, ProductDto.UpdateRequest request) {
        Product product = findProductById(id);
        product.update(
                request.getTitle(),
                request.getDescription(),
                request.getCategory(),
                request.getPrice(),
                request.getStatus()
        );
        return ProductDto.ProductResponse.from(product);
    }

    /**
     * 상품 존재 여부 확인 (Enrollment Service → Course Service REST 호출용)
     */
    public boolean existsProduct(Long id) {
        return productRepository.existsById(id);
    }

    /**
     * 상품 관련 수량 값 증가 (Enrollment Service가 발주 확정 시 호출)
     */
    @Transactional
    public void increaseEnrollmentCount(Long productId, int quantity) {
        Product product = findProductById(productId);
        product.increaseEnrollmentCount(quantity);
    }

    /**
     * 추천 서비스용: 카테고리별 미포함 상품 조회
     */
    public List<ProductDto.ProductResponse> getRecommendProducts(
            Product.Category category, List<Long> excludeProductIds) {

        List<Product> products = excludeProductIds.isEmpty()
                ? productRepository.findByCategoryAndStatus(category, Product.Status.ACTIVE)
                : productRepository.findByCategoryAndStatusAndIdNotIn(
                        category, Product.Status.ACTIVE, excludeProductIds);

        return products.stream()
                .sorted((a, b) -> b.getEnrollmentCount() - a.getEnrollmentCount())
                .map(ProductDto.ProductResponse::from)
                .collect(Collectors.toList());
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
    }
}
