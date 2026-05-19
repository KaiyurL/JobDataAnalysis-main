package com.jobdata.dto;

import com.jobdata.entity.JobInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalaryPredictResponse {
    private BigDecimal salaryMinPredicted;
    private BigDecimal salaryMaxPredicted;
    private List<JobInfo> similarJobs;
}
