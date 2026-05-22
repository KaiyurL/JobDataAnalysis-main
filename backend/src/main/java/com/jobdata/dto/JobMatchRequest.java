package com.jobdata.dto;

import lombok.Data;

import java.util.List;

@Data
public class JobMatchRequest {
    private String targetRole;
    private String city;
    private String education;
    private String experience;
    private String skills;
    private String notes;
    private List<String> highlights;
    private List<ProjectInput> projects;

    @Data
    public static class ProjectInput {
        private String name;
        private String role;
        private List<String> tech;
        private List<String> highlights;
    }
}
