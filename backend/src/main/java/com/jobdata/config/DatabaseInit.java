
package com.jobdata.config;

import com.jobdata.entity.User;
import com.jobdata.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DatabaseInit implements CommandLineRunner {
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已经有admin用户
        User admin = userService.findByUsername("admin");
        if (admin == null) {
            User user = new User();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setRole("user");
            user.setCreateTime(LocalDateTime.now());
            userService.save(user);
            System.out.println("测试用户已创建: admin / admin123");
            return;
        }

        if (!passwordEncoder.matches("admin123", admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode("admin123"));
            if (admin.getRole() == null) {
                admin.setRole("user");
            }
            userService.updateById(admin);
            System.out.println("测试用户密码已重置: admin / admin123");
        }
    }
}
