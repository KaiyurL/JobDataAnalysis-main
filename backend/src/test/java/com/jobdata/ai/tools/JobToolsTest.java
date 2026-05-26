package com.jobdata.ai.tools;

import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobToolsTest {

    @Mock
    JobInfoService jobInfoService;

    @Mock
    JobInfo51JobService jobInfo51JobService;

    @Mock
    JobToolResultStore jobToolResultStore;

    @InjectMocks
    JobTools jobTools;

    @Test
    void jobSearch_all_applies_limit_and_sets_store() {
        JobInfo boss1 = new JobInfo();
        boss1.setId(1L);
        boss1.setJobName("Java开发");
        boss1.setCompanyName("A公司");
        boss1.setCity("北京");

        JobInfo boss2 = new JobInfo();
        boss2.setId(2L);
        boss2.setJobName("后端开发");
        boss2.setCompanyName("B公司");
        boss2.setCity("上海");

        JobInfo51Job j51 = new JobInfo51Job();
        j51.setId(3L);
        j51.setJobName("数据分析");
        j51.setCompanyName("C公司");
        j51.setCity("广州");

        when(jobInfoService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<JobInfo>>any()))
                .thenReturn(List.of(boss1, boss2));
        when(jobInfo51JobService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<JobInfo51Job>>any()))
                .thenReturn(List.of(j51));

        List<Map<String, Object>> out = jobTools.jobSearch("all", "", "", "", "", null, null, "", 2);
        assertEquals(2, out.size());
        assertNotNull(out.get(0).get("jobName"));

        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobToolResultStore).setLastJobCards(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void jobSearch_boss_only() {
        JobInfo boss1 = new JobInfo();
        boss1.setId(1L);
        boss1.setJobName("Java开发");

        when(jobInfoService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<JobInfo>>any()))
                .thenReturn(List.of(boss1));

        List<Map<String, Object>> out = jobTools.jobSearch("boss", "", "", "", "", null, null, "", 10);
        assertEquals(1, out.size());
        assertEquals("boss", String.valueOf(out.get(0).get("source")));
    }
}
