package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.kafka.KafkaEvent;
import com.lecture.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceQuantityTests {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseServiceClient courseServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Mock
    private EnrollmentKafkaProducer kafkaProducer;

    @Mock
    private EnrollmentWriteService enrollmentWriteService;

    @InjectMocks
    private EnrollmentService enrollmentService;

    @Test
    void createOrderPersistsAndReturnsRequestedQuantity() {
        Enrollment order = order(7, Enrollment.Status.REQUESTED);
        when(courseServiceClient.existsProduct(20L)).thenReturn(true);
        when(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                10L,
                20L,
                List.of(Enrollment.Status.REQUESTED, Enrollment.Status.APPROVED)
        )).thenReturn(false);
        when(enrollmentWriteService.createRequestedOrder(10L, 20L, 7)).thenReturn(order);

        EnrollmentDto.OrderResponse response = enrollmentService.createOrder(10L, 20L, 7);

        assertThat(response.getQuantity()).isEqualTo(7);
        verify(enrollmentWriteService).createRequestedOrder(10L, 20L, 7);
    }

    @Test
    void blocksAnotherOrderWhileSameProductOrderIsInProgress() {
        when(courseServiceClient.existsProduct(20L)).thenReturn(true);
        when(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                10L,
                20L,
                List.of(Enrollment.Status.REQUESTED, Enrollment.Status.APPROVED)
        )).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.createOrder(10L, 20L, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("처리 중인 발주");
        verifyNoInteractions(enrollmentWriteService);
    }

    @Test
    void approveOrderSettlesSupplyPriceTimesQuantity() {
        Enrollment order = order(7, Enrollment.Status.APPROVED);
        when(enrollmentWriteService.approveOrder(1L)).thenReturn(order);
        when(courseServiceClient.getProduct(20L)).thenReturn(Map.of("price", "650.00"));

        enrollmentService.approveOrder(1L);

        verify(paymentServiceClient).requestSettlement(10L, 20L, new BigDecimal("4550.00"));
    }

    @Test
    void receiveOrderIncreasesStockAndPublishesEventWithQuantity() {
        Enrollment order = order(7, Enrollment.Status.RECEIVED);
        when(enrollmentWriteService.receiveOrder(1L, 10L)).thenReturn(order);

        enrollmentService.receiveOrder(1L, 10L);

        verify(courseServiceClient).increaseStock(20L, 7);
        ArgumentCaptor<KafkaEvent.OrderReceivedEvent> eventCaptor =
                ArgumentCaptor.forClass(KafkaEvent.OrderReceivedEvent.class);
        verify(kafkaProducer).publishOrderReceived(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getQuantity()).isEqualTo(7);
    }

    private Enrollment order(int quantity, Enrollment.Status status) {
        return Enrollment.builder()
                .id(1L)
                .userId(10L)
                .courseId(20L)
                .quantity(quantity)
                .status(status)
                .build();
    }
}
