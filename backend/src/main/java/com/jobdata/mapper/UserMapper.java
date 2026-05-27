
package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper：提供 users 表的基础 CRUD 操作。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
