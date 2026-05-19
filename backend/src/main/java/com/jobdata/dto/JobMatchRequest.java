package com.jobdata.dto;

import lombok.Data;

import java.util.List;

@Data
public class JobMatchRequest {
    private List<String> skills;
}
