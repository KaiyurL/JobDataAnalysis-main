package com.jobdata;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端应用启动入口。
 */
@SpringBootApplication
@MapperScan({"com.jobdata.mapper", "com.jobdata.ai.mapper"})
public class JobDataAnalysisApplication {

    /**
     * Spring Boot 主启动方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(JobDataAnalysisApplication.class, args);
    }

}
