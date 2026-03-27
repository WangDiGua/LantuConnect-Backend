package com.lantu.connect.common.exception;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("Business exception at {}: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(resolveHttpStatus(e.getCode()))
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<Void> handleRateLimit(RequestNotPermitted e, HttpServletRequest request) {
        log.warn("Rate limited at {}", request.getRequestURI());
        return R.fail(ResultCode.RATE_LIMITED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingRequestHeader(MissingRequestHeaderException e, HttpServletRequest request) {
        log.warn("Missing request header at {}: {}", request.getRequestURI(), e.getHeaderName());
        return R.fail(ResultCode.PARAM_ERROR, "缺少必需的请求头: " + e.getHeaderName());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return R.fail(ResultCode.FILE_SIZE_EXCEEDED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }

    private static HttpStatus resolveHttpStatus(int code) {
        if (code == ResultCode.PARAM_ERROR.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ResultCode.UNAUTHORIZED.getCode()
                || code == ResultCode.TOKEN_EXPIRED.getCode()
                || code == ResultCode.REFRESH_TOKEN_INVALID.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ResultCode.FORBIDDEN.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ResultCode.NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == ResultCode.CONFLICT.getCode()
                || code == ResultCode.DUPLICATE_SUBMIT.getCode()
                || code == ResultCode.ILLEGAL_STATE_TRANSITION.getCode()) {
            return HttpStatus.CONFLICT;
        }
        if (code == ResultCode.RATE_LIMITED.getCode()
                || code == ResultCode.DAILY_QUOTA_EXHAUSTED.getCode()
                || code == ResultCode.MONTHLY_QUOTA_EXHAUSTED.getCode()
                || code == ResultCode.CIRCUIT_OPEN.getCode()
                || code == ResultCode.QUOTA_EXCEEDED.getCode()) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
