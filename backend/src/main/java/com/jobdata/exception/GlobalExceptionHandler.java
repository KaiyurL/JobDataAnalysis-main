package com.jobdata.exception;

import com.jobdata.dto.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一将异常转换为标准 Result 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param e 业务异常
     * @return 标准结果
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        Result<Object> r = new Result<>();
        r.setCode(e.getCode() == null ? 500 : e.getCode());
        r.setMessage(e.getMessage());
        return r;
    }

    /**
     * 处理参数校验异常。
     *
     * @param e 校验异常
     * @return 标准结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult() == null || e.getBindingResult().getFieldError() == null
                ? "参数校验失败"
                : e.getBindingResult().getFieldError().getDefaultMessage();
        return Result.error(msg);
    }

    /**
     * 兜底处理未捕获异常。
     *
     * @param e 异常
     * @return 标准结果
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        return Result.error(e.getMessage() == null ? "服务异常" : e.getMessage());
    }
}
