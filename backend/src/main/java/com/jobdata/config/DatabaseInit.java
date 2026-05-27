
package com.jobdata.config;

import com.jobdata.entity.User;
import com.jobdata.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据库初始化器：在应用启动时确保存在可用的测试管理员账号。
 */
@Component
public class DatabaseInit implements CommandLineRunner {
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 应用启动回调：检查并创建/修复默认管理员账号。
     *
     * @param args 启动参数
     * @throws Exception 初始化异常
     */
    @Override
    public void run(String... args) throws Exception {
        // 检查是否已经有admin用户
        User admin = userService.findByUsername("admin");
        if (admin == null) {
            User user = new User();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setRole("admin");
            user.setCreateTime(LocalDateTime.now());
            userService.save(user);
            System.out.println("测试用户已创建: admin / admin123");
            return;
        }

        boolean changed = false;
        if (!passwordEncoder.matches("admin123", admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode("admin123"));
            changed = true;
            System.out.println("测试用户密码已重置: admin / admin123");
        }
        if (admin.getRole() == null || !"admin".equalsIgnoreCase(admin.getRole())) {
            admin.setRole("admin");
            changed = true;
        }
        if (changed) {
            userService.updateById(admin);
        }
    }
}
