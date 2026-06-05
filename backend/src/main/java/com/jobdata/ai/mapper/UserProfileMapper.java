package com.jobdata.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.ai.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户画像 Mapper：提供用户画像表的基础 CRUD 操作。
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}

