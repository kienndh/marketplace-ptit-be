package com.ptiters.marketplace.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // làm các trường null không hiện ra
public class ErrorResponse {
    private String detail;
    private Map<String, String> errors;
    private LocalDateTime timestamp;

    public ErrorResponse(String detail) {
        this.detail = detail;
        this.errors = null;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String detail, Map<String, String> errors) {
        this.detail = detail;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }
}
