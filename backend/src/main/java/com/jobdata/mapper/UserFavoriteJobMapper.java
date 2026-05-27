package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.UserFavoriteJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户收藏 Mapper：提供用户收藏表的基础 CRUD 操作。
 */
@Mapper
public interface UserFavoriteJobMapper extends BaseMapper<UserFavoriteJob> {
}
