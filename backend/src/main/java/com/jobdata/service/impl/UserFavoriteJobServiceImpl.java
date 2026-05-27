package com.jobdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.mapper.UserFavoriteJobMapper;
import com.jobdata.service.UserFavoriteJobService;
import org.springframework.stereotype.Service;

/**
 * 用户收藏服务实现：基于 MyBatis-Plus 的通用 ServiceImpl 提供 CRUD 能力。
 */
@Service
public class UserFavoriteJobServiceImpl extends ServiceImpl<UserFavoriteJobMapper, UserFavoriteJob> implements UserFavoriteJobService {
}
