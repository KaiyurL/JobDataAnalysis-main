package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    @Select("SELECT COUNT(*) AS cnt, MAX(created_at) AS maxCreatedAt FROM job_info")
    Map<String, Object> getFingerprint();
}
