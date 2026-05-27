
package com.jobdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jobdata.entity.User;

/**
 * 用户服务：提供用户查询与创建能力。
 */
public interface UserService extends IService<User> {
    /**
     * 按用户名查找用户。
     *
     * @param username 用户名
     * @return 用户（不存在返回 null）
     */
    User findByUsername(String username);

    /**
     * 创建普通用户（负责密码加密与默认角色赋值）。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 创建后的用户
     */
    User createUser(String username, String password);
}
