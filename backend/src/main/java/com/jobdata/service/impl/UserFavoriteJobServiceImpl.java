package com.jobdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.mapper.UserFavoriteJobMapper;
import com.jobdata.service.UserFavoriteJobService;
import org.springframework.stereotype.Service;

@Service
public class UserFavoriteJobServiceImpl extends ServiceImpl<UserFavoriteJobMapper, UserFavoriteJob> implements UserFavoriteJobService {
}
