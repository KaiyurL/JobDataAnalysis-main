package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.UserMatchHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户匹配历史 Mapper：提供用户匹配历史表的基础 CRUD 操作。
 */
@Mapper
public interface UserMatchHistoryMapper extends BaseMapper<UserMatchHistory> {
}
