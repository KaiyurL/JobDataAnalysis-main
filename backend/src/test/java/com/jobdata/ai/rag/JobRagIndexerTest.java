package com.jobdata.ai.rag;

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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRagIndexerTest {

    @Mock
    JobInfoService jobInfoService;

    @Mock
    JobInfo51JobService jobInfo51JobService;

    @Mock
    VectorStore vectorStore;

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    JobRagIndexer jobRagIndexer;

    @Test
    void reindexJobs_resets_and_adds_documents() {
        JobInfo boss = new JobInfo();
        boss.setId(1L);
        boss.setJobName("Java开发");
        boss.setCompanyName("A公司");
        boss.setCity("北京");

        JobInfo51Job j51 = new JobInfo51Job();
        j51.setId(2L);
        j51.setJobName("数据分析");
        j51.setCompanyName("B公司");
        j51.setCity("上海");

        when(jobInfoService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<JobInfo>>any()))
                .thenReturn(List.of(boss));
        when(jobInfo51JobService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<JobInfo51Job>>any()))
                .thenReturn(List.of(j51));

        Map<String, Object> out = jobRagIndexer.reindexJobs("all", 10, true);

        verify(jdbcTemplate).update("DELETE FROM vector_store");

        ArgumentCaptor<List<Document>> docsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docsCaptor.capture());
        assertEquals(2, docsCaptor.getValue().size());
        assertEquals(2, out.get("documents"));
    }
}
