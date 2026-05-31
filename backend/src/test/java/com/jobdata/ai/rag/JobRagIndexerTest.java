package com.jobdata.ai.rag;

import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
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

        when(jobInfoService.count()).thenReturn(1L);
        when(jobInfo51JobService.count()).thenReturn(1L);

        Page<JobInfo> bossPage = new Page<>(1, 10);
        bossPage.setRecords(List.of(boss));
        when(jobInfoService.page(org.mockito.ArgumentMatchers.any(Page.class), org.mockito.ArgumentMatchers.any()))
                .thenReturn(bossPage);

        Page<JobInfo51Job> job51Page = new Page<>(1, 10);
        job51Page.setRecords(List.of(j51));
        when(jobInfo51JobService.page(org.mockito.ArgumentMatchers.any(Page.class), org.mockito.ArgumentMatchers.any()))
                .thenReturn(job51Page);

        AtomicReference<List<Document>> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            List<Document> docs = invocation.getArgument(0);
            captured.set(List.copyOf(docs));
            return null;
        }).when(vectorStore).add(anyList());

        Map<String, Object> out = jobRagIndexer.reindexJobs("all", 10, true);

        verify(jdbcTemplate).update("DELETE FROM vector_store");
        verify(vectorStore).add(anyList());
        assertEquals(2, captured.get().size());
        assertEquals(2, out.get("documents"));
    }
}
