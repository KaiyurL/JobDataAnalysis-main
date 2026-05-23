package com.jobdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.UserProfile;
import com.jobdata.mapper.UserProfileMapper;
import com.jobdata.service.UserProfileService;
import org.springframework.stereotype.Service;

@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements UserProfileService {
}
