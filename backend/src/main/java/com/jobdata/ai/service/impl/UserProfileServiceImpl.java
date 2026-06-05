package com.jobdata.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.ai.entity.UserProfile;
import com.jobdata.ai.mapper.UserProfileMapper;
import com.jobdata.ai.service.UserProfileService;
import org.springframework.stereotype.Service;

/**
 * 用户画像服务实现：基于 MyBatis-Plus 的通用 ServiceImpl 提供 CRUD 能力。
 */
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements UserProfileService {
}

