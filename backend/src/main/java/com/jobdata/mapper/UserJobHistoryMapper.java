package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.UserJobHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户浏览历史 Mapper：提供用户职位浏览历史表的基础 CRUD 操作。
 */
@Mapper
public interface UserJobHistoryMapper extends BaseMapper<UserJobHistory> {
}
