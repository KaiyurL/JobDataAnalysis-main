package com.jobdata;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jobdata.mapper")
public class JobDataAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobDataAnalysisApplication.class, args);
    }

}
