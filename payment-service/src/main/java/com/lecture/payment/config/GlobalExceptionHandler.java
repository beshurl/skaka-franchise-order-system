package com.lecture.payment.config;

import com.lecture.payment.dto.PaymentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.lecture.payment.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(PaymentDto.ApiResponse.error(e.getMessage()));
    }

    /** 본사/가맹점 권한 위반, 타 가맹점 결제 내역 접근 (course-service/enrollment-service와 동일한 컨벤션) */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(PaymentDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(PaymentDto.ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentDto.ApiResponse<Void>> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PaymentDto.ApiResponse.error("서버 오류가 발생했습니다"));
    }
}