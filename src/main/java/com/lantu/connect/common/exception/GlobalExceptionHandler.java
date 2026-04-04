package com.lantu.connect.common.exception;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.web.TraceLogging;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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
        log.warn("Business exception code={} traceId={} uri={}: {}",
                e.getCode(),
                TraceLogging.traceIdOrDash(),
                request.getRequestURI(),
                e.getMessage());
        return ResponseEntity.status(resolveHttpStatus(e.getCode()))
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<Void> handleRateLimit(RequestNotPermitted e, HttpServletRequest request) {
        log.warn("Rate limited traceId={} uri={}", TraceLogging.traceIdOrDash(), request.getRequestURI());
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
        log.warn("Missing request header traceId={} uri={} header={}",
                TraceLogging.traceIdOrDash(), request.getRequestURI(), e.getHeaderName());
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
    public R<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation traceId={} uri={}: {}",
                TraceLogging.traceIdOrDash(), request.getRequestURI(), abbreviateLogMessage(msg, 2000));
        return R.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("Max upload exceeded traceId={} uri={}: {}",
                TraceLogging.traceIdOrDash(), request.getRequestURI(), e.getMessage());
        return R.fail(ResultCode.FILE_SIZE_EXCEEDED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("No resource found traceId={} uri={} resourcePath={}",
                TraceLogging.traceIdOrDash(), request.getRequestURI(), e.getResourcePath());
        return R.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 常见原因：仅注册了 multipart 的后端版本上却以 application/json 调用；或反向。
     * 当前技能包上传同时支持 multipart 与 JSON（Base64），仍不匹配时请核对路径与 Content-Type。
     */
    /**
     * 常见于前端误将 multipart 发成 JSON（如 file 被序列化为空对象），或字段类型与 DTO 不符。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("HTTP message not readable traceId={} uri={}: {}",
                TraceLogging.traceIdOrDash(), request.getRequestURI(), abbreviateLogMessage(e.getMessage(), 500));
        String msg = "请求体无法解析。技能包上传请使用 multipart/form-data（字段 file 为文件二进制），"
                + "或使用 application/json 且字段 file / fileBase64 为 Base64 字符串；勿将 file 传为 JSON 对象。";
        return R.fail(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e,
                                                              HttpServletRequest request) {
        log.warn("Unsupported media type traceId={} uri={}: {}",
                TraceLogging.traceIdOrDash(), request.getRequestURI(), e.getMessage());
        String msg = "Content-Type 与接口不匹配。技能包上传请用 multipart/form-data（字段 file），"
                + "或 application/json（字段 file / fileBase64 为文件 Base64）；并确保后端已更新至支持 JSON 的版本。";
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(R.fail(ResultCode.PARAM_ERROR, msg));
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleDataAccess(DataAccessException e, HttpServletRequest request) {
        String root = e.getMostSpecificCause() == null ? "" : String.valueOf(e.getMostSpecificCause().getMessage());
        log.error("Data access error traceId={} uri={}: {}",
                TraceLogging.traceIdOrDash(),
                request.getRequestURI(),
                abbreviateLogMessage(root, 2000),
                e);
        if (root.contains("Unknown column")
                || root.contains("doesn't exist")
                || root.contains("does not exist")) {
            return R.fail(ResultCode.INTERNAL_ERROR,
                    "数据库表结构可能未更新，请按 README 说明执行 sql/migrations 与 sql/incremental 中的增量脚本");
        }
        return R.fail(ResultCode.INTERNAL_ERROR, "数据访问失败，请稍后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public R<Void> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Data integrity traceId={} uri={}: {}",
                TraceLogging.traceIdOrDash(),
                request.getRequestURI(),
                abbreviateLogMessage(String.valueOf(e.getMostSpecificCause().getMessage()), 2000));
        String msg = e.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("uk_provider_code")) {
            return R.fail(ResultCode.DUPLICATE_NAME, "provider_code 已存在");
        }
        return R.fail(ResultCode.CONFLICT, "数据约束冲突，请检查唯一键与关联数据");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception traceId={} uri={}", TraceLogging.traceIdOrDash(), request.getRequestURI(), e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }

    private static String abbreviateLogMessage(String msg, int max) {
        if (msg == null) {
            return "";
        }
        if (msg.length() <= max) {
            return msg;
        }
        return msg.substring(0, max) + "...";
    }

    private static HttpStatus resolveHttpStatus(int code) {
        if (code == ResultCode.PARAM_ERROR.getCode()
                || code == ResultCode.GATEWAY_API_KEY_REQUIRED.getCode()
                || code == ResultCode.REJECT_REASON_REQUIRED.getCode()
                || code == ResultCode.CANNOT_REVIEW_OWN.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ResultCode.UNAUTHORIZED.getCode()
                || code == ResultCode.TOKEN_EXPIRED.getCode()
                || code == ResultCode.REFRESH_TOKEN_INVALID.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ResultCode.FORBIDDEN.getCode()
                || code == ResultCode.DATASET_ACCESS_DENIED.getCode()
                || code == ResultCode.CANNOT_DELETE_SYSTEM_ROLE.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ResultCode.NOT_FOUND.getCode()
                || code == ResultCode.GRANT_APPLICATION_NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == ResultCode.CONFLICT.getCode()
                || code == ResultCode.DUPLICATE_SUBMIT.getCode()
                || code == ResultCode.DUPLICATE_NAME.getCode()
                || code == ResultCode.DUPLICATE_VERSION.getCode()
                || code == ResultCode.CANNOT_DELETE_PUBLISHED.getCode()
                || code == ResultCode.FAVORITE_EXISTS.getCode()
                || code == ResultCode.GRANT_APPLICATION_DUPLICATE.getCode()
                || code == ResultCode.GRANT_APPLICATION_NOT_PENDING.getCode()
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
