package com.jobdata.exception;

/**
 * 业务异常：用于表达可预期的业务失败场景（例如参数不合法、资源不存在、权限不足等）。
 */
public class BizException extends RuntimeException {
    private final Integer code;

    /**
     * 创建业务异常（默认 code=500）。
     *
     * @param message 异常信息
     */
    public BizException(String message) {
        this(500, message);
    }

    /**
     * 创建业务异常。
     *
     * @param code 业务错误码
     * @param message 异常信息
     */
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码。
     *
     * @return 错误码
     */
    public Integer getCode() {
        return code;
    }
}
