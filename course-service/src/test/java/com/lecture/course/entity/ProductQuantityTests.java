package com.lecture.course.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductQuantityTests {

    @Test
    void increasesInventoryByReceivedQuantity() {
        Product product = Product.builder()
                .enrollmentCount(20)
                .build();

        product.increaseEnrollmentCount(30);

        assertThat(product.getEnrollmentCount()).isEqualTo(50);
    }

    @Test
    void rejectsOutOfRangeQuantity() {
        Product product = Product.builder()
                .enrollmentCount(20)
                .build();

        assertThatThrownBy(() -> product.increaseEnrollmentCount(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.increaseEnrollmentCount(1000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
