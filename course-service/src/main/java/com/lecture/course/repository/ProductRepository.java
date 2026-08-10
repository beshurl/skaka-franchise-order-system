package com.lecture.course.repository;

import com.lecture.course.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 카테고리별 판매중 상품 조회
    List<Product> findByCategoryAndStatus(Product.Category category, Product.Status status);

    // 본사 관리자별 등록 상품 조회
    List<Product> findByInstructorId(Long instructorId);

    // 판매중 상품 전체 조회
    List<Product> findByStatus(Product.Status status);

    // 카테고리별 + 특정 ID 제외 조회
    List<Product> findByCategoryAndStatusAndIdNotIn(
            Product.Category category,
            Product.Status status,
            List<Long> excludeIds
    );
}
