
package com.jobdata.controller;

import com.jobdata.util.JwtUtil;
import com.jobdata.dto.LoginRequest;
import com.jobdata.dto.LoginResponse;
import com.jobdata.dto.Result;
import com.jobdata.entity.User;
import com.jobdata.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：提供登录与注册能力，并签发 JWT。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 用户登录：校验用户名密码，返回 JWT 和用户信息。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = userService.findByUsername(request.getUsername());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.fail("密码错误");
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole());
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRole());
        return Result.success(new LoginResponse(token, userInfo));
    }

    /**
     * 用户注册：创建新用户。
     *
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody LoginRequest request) {
        User existUser = userService.findByUsername(request.getUsername());
        if (existUser != null) {
            return Result.fail("用户名已存在");
        }
        userService.createUser(request.getUsername(), request.getPassword());
        return Result.success("注册成功");
    }
}
