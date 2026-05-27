package com.jobdata.dto;

import lombok.Data;

/**
 * 通用响应包装：用于统一接口返回结构。
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    /**
     * 构造成功响应。
     *
     * @param data 响应数据
     * @return Result
     * @param <T> 数据类型
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /**
     * 构造业务失败响应（code=500）。
     *
     * @param message 失败信息
     * @return Result
     * @param <T> 数据类型
     */
    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    /**
     * 构造错误响应（code=500）。
     *
     * @param message 错误信息
     * @return Result
     * @param <T> 数据类型
     */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }
}
