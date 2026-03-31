package com.lantu.connect.common.result;

import lombok.Data;

/**
 * 统一响应包装
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class R<T> {

    private int code;
    private T data;
    private String message;
    private long timestamp;

    private R() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        R<T> r = new R<>();
        r.code = resultCode.getCode();
        r.message = resultCode.getMessage();
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        R<T> r = new R<>();
        r.code = resultCode.getCode();
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /** 指定业务码与正文（如网关 invoke 在 HTTP 非 2xx 时仍返回结构化 data）。 */
    public static <T> R<T> of(int code, String message, T data) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }
}
