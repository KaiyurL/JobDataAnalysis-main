package com.jobdata.ai;

import com.jobdata.ai.model.AgentStreamEvent;
import com.jobdata.ai.service.AgentChatService;
import com.jobdata.ai.tools.UserTools;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import com.jobdata.dto.AiChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceChatStreamEndEventTest {

    @Mock
    ChatClient.Builder builder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    @Mock
    VectorStore vectorStore;

    @Mock
    UserTools userTools;

    @Mock
    JobInfoService jobInfoService;

    @Mock
    JobInfo51JobService jobInfo51JobService;

    @Test
    void chatStream_end_payload_jobCards_comes_from_rag_candidates_and_has_stable_shape() {
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(
                        "{\"source\":\"boss\",\"keyword\":\"Java\",\"city\":\"北京\",\"limit\":1}",
                        "{\"selected\":[{\"index\":1,\"score\":90,\"reason\":\"match\"}]}"
                );
        when(chatClient.prompt().system(anyString()).user(anyString()).stream().content())
                .thenReturn(Flux.just("OK"));

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

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(), List.of(doc), List.of(doc));

        AgentChatService agentChatService = new AgentChatService(builder, vectorStore, userTools, jobInfoService, jobInfo51JobService);

        AiChatRequest req = new AiChatRequest();
        req.setMessage("我想找北京Java开发");

        List<ServerSentEvent<AgentStreamEvent>> events = agentChatService.chatStream(req, null)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertNotNull(events);
        assertEquals(3, events.size());

        assertEquals("start", dataOf(events.get(0)).getType());

        AgentStreamEvent d1 = dataOf(events.get(1));
        assertEquals("delta", d1.getType());
        assertEquals("OK", d1.getText());

        AgentStreamEvent endEvent = dataOf(events.get(2));
        assertEquals("end", endEvent.getType());
        assertEquals("", endEvent.getText());

        Map<String, Object> payload = endEvent.getPayload();
        assertNotNull(payload);
        Object jobCardsObj = payload.get("jobCards");
        assertTrue(jobCardsObj instanceof List);

        List<?> cards = (List<?>) jobCardsObj;
        assertEquals(1, cards.size());
        assertTrue(cards.get(0) instanceof Map);

        Map<?, ?> card = (Map<?, ?>) cards.get(0);
        assertEquals("boss", card.get("source"));
        assertEquals(1L, card.get("id"));
        assertEquals("Java开发", card.get("jobName"));
        assertEquals("某科技公司", card.get("companyName"));
        assertEquals("北京", card.get("city"));
        assertEquals("https://example.com/j/1", card.get("jobUrl"));
        assertEquals("3-5年", card.get("experience"));
        assertEquals("本科", card.get("education"));
        assertEquals("JD", card.get("jobDesc"));
        assertEquals("Java Spring", card.get("jobKeywords"));
        assertEquals("互联网", card.get("companyIndustry"));
        assertEquals("100-499人", card.get("companySize"));
        assertEquals("五险一金", card.get("companyWelfare"));
        assertEquals(LocalDate.parse("2026-01-02"), card.get("publishDate"));
        assertEquals(20, card.get("salaryMin"));
        assertEquals(35, card.get("salaryMax"));
        assertEquals(90, card.get("matchScore"));
        assertEquals("match", card.get("aiReason"));

        Set<String> keys = card.keySet().stream().map(String::valueOf).collect(Collectors.toSet());
        assertEquals(Set.of(
                "source",
                "id",
                "jobName",
                "companyName",
                "city",
                "jobUrl",
                "experience",
                "education",
                "jobDesc",
                "jobKeywords",
                "companyIndustry",
                "companySize",
                "companyWelfare",
                "publishDate",
                "salaryMin",
                "salaryMax",
                "matchScore",
                "aiReason"
        ), keys);

        verifyNoInteractions(jobInfoService);
        verifyNoInteractions(jobInfo51JobService);
    }

    private static AgentStreamEvent dataOf(ServerSentEvent<AgentStreamEvent> sse) {
        AgentStreamEvent e = sse == null ? null : sse.data();
        assertNotNull(e);
        return e;
    }
}
