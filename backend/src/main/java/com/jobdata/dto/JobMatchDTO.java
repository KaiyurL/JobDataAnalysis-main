package com.jobdata.dto;

import com.jobdata.entity.JobInfo;
import lombok.Data;

@Data
public class JobMatchDTO {
    private JobInfo job;
    private Double matchScore;
    private String matchReason;
    private String sourceTable;
}
