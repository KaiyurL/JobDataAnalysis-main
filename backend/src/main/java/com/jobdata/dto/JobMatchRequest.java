package com.jobdata.dto;

import lombok.Data;

@Data
public class JobMatchRequest {
    private String targetRole;
    private String city;
    private String education;
    private String experience;
    private String skills;
}
