package com.jobdata.ai;

import com.jobdata.ai.service.AgentChatService;
import com.jobdata.ai.tools.UserTools;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceCandidatesRagTest {

    @Mock
    ChatClient.Builder builder;

    @Mock
    VectorStore vectorStore;

    @Mock
    UserTools userTools;

    @Mock
    JobInfoService jobInfoService;

    @Mock
    JobInfo51JobService jobInfo51JobService;

    @Test
    void fetchCandidatesByRag_maps_docs_to_job_cards() throws Exception {
        AgentChatService agentChatService = new AgentChatService(builder, vectorStore, userTools, jobInfoService, jobInfo51JobService);

        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "boss");
        meta.put("source_table", "job_info");
        meta.put("job_id", 1L);
        meta.put("job_url", "https://example.com/j/1");
        meta.put("job_name", "Java开发");
        meta.put("company_name", "某科技公司");
        meta.put("city", "北京");
        meta.put("education", "本科");
        meta.put("experience", "3-5年");
        meta.put("salary_min", 20);
        meta.put("salary_max", 35);
        meta.put("job_keywords", "Java Spring");
        meta.put("job_desc", "JD");
        meta.put("company_industry", "互联网");
        meta.put("company_size", "100-499人");
        meta.put("company_welfare", "五险一金");
        meta.put("publish_date", LocalDate.parse("2026-01-02"));
        Document doc = new Document("text", meta);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        Method m = AgentChatService.class.getDeclaredMethod("fetchCandidatesByRag", String.class, Map.class, Map.class);
        m.setAccessible(true);
        List<Map<String, Object>> out = (List<Map<String, Object>>) m.invoke(agentChatService, "我想找北京Java开发", Map.of(), Map.of("source", "boss", "keyword", "Java", "city", "北京", "limit", 10));

        assertNotNull(out);
        assertEquals(1, out.size());
        Map<String, Object> c = out.get(0);
        assertEquals("boss", c.get("source"));
        assertEquals(1L, c.get("id"));
        assertEquals("Java开发", c.get("jobName"));
        assertEquals("某科技公司", c.get("companyName"));
        assertEquals("北京", c.get("city"));
        assertEquals(20, c.get("salaryMin"));
        assertEquals(35, c.get("salaryMax"));
        assertEquals("JD", c.get("jobDesc"));
        assertEquals("Java Spring", c.get("jobKeywords"));
    }

    @Test
    void fetchCandidatesByRag_returns_empty_when_rag_empty_and_does_not_query_db() throws Exception {
        AgentChatService agentChatService = new AgentChatService(builder, vectorStore, userTools, jobInfoService, jobInfo51JobService);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(), List.of());

        Method m = AgentChatService.class.getDeclaredMethod("fetchCandidatesByRag", String.class, Map.class, Map.class);
        m.setAccessible(true);
        List<Map<String, Object>> out = (List<Map<String, Object>>) m.invoke(agentChatService, "我想找北京Java开发", Map.of(), Map.of("source", "boss", "keyword", "Java", "city", "北京", "limit", 10));

        assertNotNull(out);
        assertEquals(0, out.size());
        verifyNoInteractions(jobInfoService);
        verifyNoInteractions(jobInfo51JobService);
    }

    @Test
    void fetchCandidatesByRag_does_not_enrich_from_db() throws Exception {
        AgentChatService agentChatService = new AgentChatService(builder, vectorStore, userTools, jobInfoService, jobInfo51JobService);

        Document doc = new Document("text", Map.of(
                "source", "boss",
                "source_table", "job_info",
                "job_id", 1L
        ));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        Method m = AgentChatService.class.getDeclaredMethod("fetchCandidatesByRag", String.class, Map.class, Map.class);
        m.setAccessible(true);
        List<Map<String, Object>> out = (List<Map<String, Object>>) m.invoke(agentChatService, "我想找北京Java开发", Map.of(), Map.of("source", "boss", "keyword", "Java", "city", "北京", "limit", 10));

        assertNotNull(out);
        assertEquals(1, out.size());
        Map<String, Object> c = out.get(0);
        assertEquals("boss", c.get("source"));
        assertEquals(1L, c.get("id"));
        verifyNoInteractions(jobInfoService);
        verifyNoInteractions(jobInfo51JobService);
    }
}
