
package com.jobdata.dto;

import lombok.Data;

/**
 * 登录/注册请求 DTO：包含用户名与密码。
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}
