package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.JobInfo51Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface JobInfo51JobMapper extends BaseMapper<JobInfo51Job> {
    @Select("SELECT COUNT(*) AS cnt, MAX(created_at) AS maxCreatedAt FROM job_info_51job")
    Map<String, Object> getFingerprint();
}
