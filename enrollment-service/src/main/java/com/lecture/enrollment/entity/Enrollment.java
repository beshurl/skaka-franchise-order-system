package com.lecture.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 발주 (Order)
 * - 테이블명과 컬럼명은 기존 스켈레톤을 그대로 사용한다.
 *   user_id   : 발주한 가맹점 관리자 ID
 *   course_id : 발주 대상 상품 ID
 */
@Entity
@Table(name = "enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "course_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 가맹점 관리자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 상품 ID */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.REQUESTED;

    /** 본사 반려 사유 (반려된 경우에만 값이 있음) */
    @Column(name = "reject_reason")
    private String rejectReason;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * 발주 상태
     * REQUESTED -> APPROVED -> RECEIVED
     *      |
     *      +---> REJECTED
     */
    public enum Status {
        REQUESTED,  // 가맹점 발주 요청, 본사 승인 대기
        APPROVED,   // 본사 승인 완료, 입고 대기
        REJECTED,   // 본사 반려
        RECEIVED    // 가맹점 입고 확인 완료
    }

    /** 본사 승인: REQUESTED 에서만 가능 */
    public void approve() {
        requireStatus(Status.REQUESTED, "승인");
        this.status = Status.APPROVED;
    }

    /** 본사 반려: REQUESTED 에서만 가능 */
    public void reject(String reason) {
        requireStatus(Status.REQUESTED, "반려");
        this.status = Status.REJECTED;
        this.rejectReason = reason;
    }

    /** 가맹점 입고 확인: APPROVED 에서만 가능 (중복 입고 방지) */
    public void receive() {
        requireStatus(Status.APPROVED, "입고 확인");
        this.status = Status.RECEIVED;
    }

    public boolean isOwnedBy(Long storeId) {
        return this.userId != null && this.userId.equals(storeId);
    }

    private void requireStatus(Status required, String action) {
        if (this.status != required) {
            throw new IllegalStateException(
                    "현재 발주 상태가 " + this.status + " 이므로 " + action + "할 수 없습니다 (필요 상태: " + required + ")");
        }
    }
}
