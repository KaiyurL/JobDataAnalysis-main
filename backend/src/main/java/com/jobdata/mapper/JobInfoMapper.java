package com.jobdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobdata.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 职位表 Mapper：提供 job_info 表的基础 CRUD 操作与数据指纹查询能力。
 */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    /**
     * 获取 job_info 表数据指纹（总数与最新创建时间），用于判断数据是否发生变化。
     *
     * @return 指纹信息
     */
    @Select("SELECT COUNT(*) AS cnt, MAX(created_at) AS maxCreatedAt FROM job_info")
    Map<String, Object> getFingerprint();
}
