package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.UserJobHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserJobHistoryMapper extends BaseMapper<UserJobHistory> {
}
