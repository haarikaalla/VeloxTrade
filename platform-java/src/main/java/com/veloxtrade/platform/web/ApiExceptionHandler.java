package com.veloxtrade.platform.web;

import com.veloxtrade.platform.dto.ApiError;
import com.veloxtrade.platform.service.TradingRuleException;
import com.veloxtrade.platform.service.UpstreamUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts service failures into stable, non-leaking JSON error bodies. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(TradingRuleException.class)
    public ResponseEntity<ApiError> onTradingRule(TradingRuleException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), List.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> onBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", List.of());
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ApiError> onUpstream(UpstreamUnavailableException ex) {
        log.warn("Upstream dependency failed: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "A market service is temporarily unavailable", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", List.of());
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message,
                                                  List<String> details) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), message, details));
    }
}
