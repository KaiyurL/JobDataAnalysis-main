
package com.jobdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.User;
import com.jobdata.mapper.UserMapper;
import com.jobdata.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务实现：提供用户查询与创建逻辑，并负责密码加密与默认角色设置。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 按用户名查找用户。
     *
     * @param username 用户名
     * @return 用户（不存在返回 null）
     */
    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 创建普通用户。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 创建后的用户
     */
    @Override
    public User createUser(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("user");
        user.setCreateTime(LocalDateTime.now());
        save(user);
        return user;
    }
}
