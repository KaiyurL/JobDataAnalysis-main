
package com.jobdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jobdata.entity.User;

public interface UserService extends IService<User> {
    User findByUsername(String username);
    User createUser(String username, String password);
}

