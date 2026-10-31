package com.ptiters.marketplace.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;

@Slf4j
@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {
    private static final int UNPROCESSABLE = 422;
    private static final String INVALID_DATA = "Dữ liệu không hợp lệ";

    // lỗi nghiệp vụ
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException  exception){
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object>  handleConstraintViolation(ConstraintViolationException exception){
        var errors = new LinkedHashMap<String, String>();
        for(var error : exception.getConstraintViolations()){
            var key = error.getPropertyPath().toString();
            var value = error.getMessage();
            errors.put(key, value);
        }
        return ResponseEntity.status(UNPROCESSABLE).body(new ErrorResponse(INVALID_DATA, errors));
    }

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        for (var error : exception.getBindingResult().getFieldErrors()) {
            var key = error.getField();
            var value = error.getDefaultMessage();
            errors.put(key, value);
        }
        return ResponseEntity.status(UNPROCESSABLE)
                .body(new ErrorResponse(INVALID_DATA, errors));
    }

    @Override
    public ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(UNPROCESSABLE)
                .body(new ErrorResponse("Malformed or unreadable request body."));
    }

    @Override
    public ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request){
        return ResponseEntity.status(UNPROCESSABLE)
                .body(new ErrorResponse("Invalid value for request parameter."));
    }

    @Override
    public ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String detail = (body instanceof ProblemDetail problem && problem.getDetail() != null)
                ? problem.getDetail()
                : "Request failed.";
        return ResponseEntity.status(statusCode).headers(headers)
                .body(new ErrorResponse(detail));
    }
}
