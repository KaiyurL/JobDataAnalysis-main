package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
