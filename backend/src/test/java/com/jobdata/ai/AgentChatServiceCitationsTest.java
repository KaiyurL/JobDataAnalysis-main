package com.jobdata.ai;

import com.jobdata.ai.service.AgentChatService;
import com.jobdata.ai.tools.JobToolResultStore;
import com.jobdata.ai.tools.JobTools;
import com.jobdata.ai.tools.UserTools;
import com.jobdata.entity.JobInfo;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceCitationsTest {

    @Mock
    ChatClient.Builder builder;

    @Mock
    ChatClient chatClient;

    @Mock
    VectorStore vectorStore;

    @Mock
    JobTools jobTools;

    @Mock
    UserTools userTools;

    @Mock
    JobToolResultStore jobToolResultStore;

    @Mock
    JobInfoService jobInfoService;

    @Mock
    JobInfo51JobService jobInfo51JobService;

    @Test
    void buildCitations_enriches_from_db_when_metadata_missing() throws Exception {
        AgentChatService agentChatService = new AgentChatService(builder, vectorStore, jobTools, userTools, jobToolResultStore, jobInfoService, jobInfo51JobService);

        Document doc = new Document("text", Map.of(
                "source_table", "job_info",
                "job_id", 1L
        ));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        JobInfo job = new JobInfo();
        job.setId(1L);
        job.setJobUrl("https://example.com/j/1");
        job.setJobKeywords("Java, Spring");
        job.setJobDesc("JD");
        job.setCompanyIndustry("互联网");
        job.setCompanySize("100-499人");
        job.setCompanyWelfare("五险一金");
        job.setPublishDate(LocalDate.parse("2026-01-02"));
        job.setSalaryMin(10);
        job.setSalaryMax(20);
        when(jobInfoService.getById(1L)).thenReturn(job);

        Method m = AgentChatService.class.getDeclaredMethod("buildCitations", String.class, Map.class);
        m.setAccessible(true);
        List<Map<String, Object>> out = (List<Map<String, Object>>) m.invoke(agentChatService, "query", null);

        assertNotNull(out);
        assertEquals(1, out.size());
        Map<String, Object> c = out.get(0);

        assertEquals("互联网", c.get("company_industry"));
        assertEquals("100-499人", c.get("company_size"));
        assertEquals("五险一金", c.get("company_welfare"));
        assertEquals("Java, Spring", c.get("job_keywords"));
        assertEquals("JD", c.get("job_desc"));
        assertEquals("https://example.com/j/1", c.get("job_url"));
        assertEquals(10, c.get("salaryMin"));
        assertEquals(20, c.get("salaryMax"));
    }
}
