package com.jobdata.ai;

import com.jobdata.ai.dto.AgentStreamEvent;
import com.jobdata.ai.service.AgentChatService;
import com.jobdata.ai.tools.UserTools;
import com.jobdata.dto.AiChatRequest;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceChatStreamUnicodeSplitTest {

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
    void chatStream_delta_should_be_streamed_from_chatclient_without_manual_chunking() {
        String reply = "a".repeat(63) + "😀" + "b".repeat(10);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(
                        "{\"source\":\"boss\",\"keyword\":\"Java\",\"city\":\"北京\",\"limit\":1}",
                        "{\"selected\":[{\"index\":1,\"score\":90,\"reason\":\"match\"}]}"
                );
        when(chatClient.prompt().system(anyString()).user(anyString()).stream().content())
                .thenReturn(Flux.just(reply));

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
        Document doc = new Document("text", meta);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc), List.of(doc), List.of(doc));

        AgentChatService agentChatService = new AgentChatService(builder, vectorStore, userTools, jobInfoService, jobInfo51JobService);

        AiChatRequest req = new AiChatRequest();
        req.setMessage("test");

        List<ServerSentEvent<AgentStreamEvent>> events = agentChatService.chatStream(req, null)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertNotNull(events);
        assertEquals(3, events.size());

        AgentStreamEvent start = dataOf(events.get(0));
        assertEquals("start", start.getType());

        AgentStreamEvent d1 = dataOf(events.get(1));
        assertEquals("delta", d1.getType());
        assertEquals(reply, d1.getText());

        AgentStreamEvent end = dataOf(events.get(2));
        assertEquals("end", end.getType());
    }

    private static AgentStreamEvent dataOf(ServerSentEvent<AgentStreamEvent> sse) {
        AgentStreamEvent e = sse == null ? null : sse.data();
        assertNotNull(e);
        return e;
    }

    
}
