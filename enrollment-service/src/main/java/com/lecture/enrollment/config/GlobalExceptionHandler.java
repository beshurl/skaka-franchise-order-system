package com.lecture.enrollment.config;

import com.lecture.enrollment.dto.EnrollmentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 존재하지 않는 발주/상품 등 요청 값 오류 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<EnrollmentDto.ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(EnrollmentDto.ApiResponse.error(e.getMessage()));
    }

    /** 허용되지 않은 상태 전이, 중복 발주, 중복 입고 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<EnrollmentDto.ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(EnrollmentDto.ApiResponse.error(e.getMessage()));
    }

    /** 본사/가맹점 권한 위반, 타 가맹점 발주 접근 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<EnrollmentDto.ApiResponse<Void>> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(EnrollmentDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EnrollmentDto.ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(EnrollmentDto.ApiResponse.error(message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<EnrollmentDto.ApiResponse<Void>> handleRuntime(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(EnrollmentDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EnrollmentDto.ApiResponse<Void>> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(EnrollmentDto.ApiResponse.error("서버 오류가 발생했습니다"));
    }
}
