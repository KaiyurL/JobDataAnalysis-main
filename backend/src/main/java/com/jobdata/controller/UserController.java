
package com.jobdata.controller;

import com.jobdata.config.JwtUtil;
import com.jobdata.dto.LoginResponse;
import com.jobdata.dto.Result;
import com.jobdata.entity.User;
import com.jobdata.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/info")
    public Result<LoginResponse.UserInfo> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.getById(userId);
        if (user != null) {
            return Result.success(new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRole()));
        }
        return Result.fail("用户不存在");
    }
}

