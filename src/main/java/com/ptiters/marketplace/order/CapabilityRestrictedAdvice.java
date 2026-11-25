package com.ptiters.marketplace.order;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Render {@link CapabilityRestrictedException} thành {@code {"detail": {"code", "message"}}} (giống Python / frontend
 * {@code apiErrors.ts}). Ưu tiên cao hơn {@code ErrorHandler} chung để bắt đúng lớp con của ApiException.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CapabilityRestrictedAdvice {

    @ExceptionHandler(CapabilityRestrictedException.class)
    public ResponseEntity<Map<String, Object>> handle(CapabilityRestrictedException ex) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", ex.getCode());
        detail.put("message", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", detail);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
