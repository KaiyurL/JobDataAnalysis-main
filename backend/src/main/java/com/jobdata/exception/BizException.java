package com.jobdata.exception;

/**
 * 业务异常：用于表达可预期的业务失败场景（例如参数不合法、资源不存在、权限不足等）。
 */
public class BizException extends RuntimeException {
    private final Integer code;

    public BizException(String message) {
        this(500, message);
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
