package com.lecture.enrollment.repository;

import com.lecture.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 가맹점 발주 목록 (userId = 가맹점 관리자 ID) */
    List<Enrollment> findByUserIdOrderByIdDesc(Long userId);

    /** 가맹점 발주 목록 - 상태 필터 */
    List<Enrollment> findByUserIdAndStatusOrderByIdDesc(Long userId, Enrollment.Status status);

    /** 본사 전체 발주 목록 */
    List<Enrollment> findAllByOrderByIdDesc();

    /** 본사 전체 발주 목록 - 상태 필터 */
    List<Enrollment> findByStatusOrderByIdDesc(Enrollment.Status status);

    /** 가맹점/상품 단위 발주 조회 (정산 이벤트 매칭용) */
    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    /** 동일 상품 중복 발주 여부 (uq_store_product_order 제약과 동일 조건) */
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    /** 입고 완료(RECEIVED)된 상품 ID 조회용 */
    List<Enrollment> findByUserIdAndStatus(Long userId, Enrollment.Status status);
}
