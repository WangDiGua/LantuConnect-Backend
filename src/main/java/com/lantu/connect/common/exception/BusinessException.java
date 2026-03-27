package com.lantu.connect.common.exception;

import com.lantu.connect.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
