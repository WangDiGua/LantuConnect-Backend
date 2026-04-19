package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderProtocolAdapterModelResolutionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void openAiCompatiblePrefersModelAliasOverUpstreamAgentId() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://example.com/v1/chat/completions",
                Map.of("input", "hello"),
                Map.of("modelAlias", "chef_agent_01", "upstreamAgentId", "fbf99ec7ae28405fbe5864fe7912c227"),
                "secret",
                "trace-1");

        assertEquals("chef_agent_01", OBJECT_MAPPER.readTree(request.body()).path("model").asText());
    }

    @Test
    void openAiCompatibleFallsBackToUpstreamAgentIdWhenAliasMissing() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://example.com/v1/chat/completions",
                Map.of("input", "hello"),
                Map.of("upstreamAgentId", "qwen-max"),
                "secret",
                "trace-2");

        assertEquals("qwen-max", OBJECT_MAPPER.readTree(request.body()).path("model").asText());
    }

    @Test
    void openAiCompatibleBuildsResponsesInputPayload() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://example.com/v1/responses",
                Map.of("input", "hello"),
                Map.of("modelAlias", "chef_agent_01", "upstreamAgentId", "fbf99ec7ae28405fbe5864fe7912c227"),
                "secret",
                "trace-2");

        assertEquals("chef_agent_01", OBJECT_MAPPER.readTree(request.body()).path("model").asText());
        assertEquals("hello", OBJECT_MAPPER.readTree(request.body()).path("input").path(0).path("content").path(0).path("text").asText());
    }

    @Test
    void anthropicPrefersModelAliasOverUpstreamAgentId() throws Exception {
        AnthropicMessagesAdapter adapter = new AnthropicMessagesAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://example.com/v1/messages",
                Map.of("input", "hello"),
                Map.of("modelAlias", "claude-3-5-sonnet-latest", "upstreamAgentId", "app-123"),
                "secret",
                "trace-3");

        assertEquals("claude-3-5-sonnet-latest", OBJECT_MAPPER.readTree(request.body()).path("model").asText());
    }

    @Test
    void bailianCompatibleBuildsResponsesInputPayload() throws Exception {
        BailianCompatibleAdapter adapter = new BailianCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://dashscope.aliyuncs.com/api/v2/apps/agent/fbf99ec7ae28405fbe5864fe7912c227/compatible-mode/v1/responses",
                Map.of("input", "hello"),
                Map.of("modelAlias", "bailian_agent_fbf99ec7ae28405fbe5864fe7912c227", "upstreamAgentId", "bailian_agent_fbf99ec7ae28405fbe5864fe7912c227"),
                "secret",
                "trace-4");

        assertEquals("hello", OBJECT_MAPPER.readTree(request.body()).path("input").path(0).path("content").path(0).path("text").asText());
        assertEquals("bailian_agent_fbf99ec7ae28405fbe5864fe7912c227", OBJECT_MAPPER.readTree(request.body()).path("customized_model_id").asText());
    }

    @Test
    void bailianCompatibleBuildsCompletionPayload() throws Exception {
        BailianCompatibleAdapter adapter = new BailianCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://dashscope.aliyuncs.com/api/v1/apps/fbf99ec7ae28405fbe5864fe7912c227/completion",
                Map.of("input", "hello"),
                Map.of("modelAlias", "bailian_agent_fbf99ec7ae28405fbe5864fe7912c227", "upstreamAgentId", "fbf99ec7ae28405fbe5864fe7912c227"),
                "secret",
                "trace-5");

        assertEquals("hello", OBJECT_MAPPER.readTree(request.body()).path("input").path("prompt").asText());
        assertEquals("bailian_agent_fbf99ec7ae28405fbe5864fe7912c227", OBJECT_MAPPER.readTree(request.body()).path("customized_model_id").asText());
    }

    @Test
    void bailianCompatibleExtractsTextFromResponsesPayload() {
        BailianCompatibleAdapter adapter = new BailianCompatibleAdapter();

        String text = adapter.extractText(OBJECT_MAPPER.valueToTree(Map.of(
                "output_text", "",
                "output", List.of(Map.of(
                        "content", List.of(Map.of("text", "done"))
                ))
        )));

        assertEquals("done", text);
    }

    @Test
    void openAiCompatibleBuildsDifyChatPayloadWhenAdapterIsDify() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://api.dify.ai/v1/chat-messages",
                Map.of("input", "hello", "session_id", "session-1"),
                Map.of("x_adapter_id", "dify", "transformProfile", "dify_agent_app"),
                "secret",
                "trace-6");

        assertEquals("hello", OBJECT_MAPPER.readTree(request.body()).path("query").asText());
        assertEquals("streaming", OBJECT_MAPPER.readTree(request.body()).path("response_mode").asText());
        assertEquals("session-1", OBJECT_MAPPER.readTree(request.body()).path("user").asText());
        assertEquals("Bearer secret", request.headers().get("Authorization"));
        assertEquals("text/event-stream", request.headers().get("Accept"));
    }

    @Test
    void openAiCompatibleBuildsAppBuilderPayloadWhenAdapterIsAppBuilder() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://qianfan.baidubce.com/v2/agent/ai_assistant/run",
                Map.of("input", "hello", "session_id", "session-2"),
                Map.of("x_adapter_id", "appbuilder", "transformProfile", "appbuilder_agent_app"),
                "secret",
                "trace-7");

        assertEquals("hello", OBJECT_MAPPER.readTree(request.body()).path("query").asText());
        assertEquals(false, OBJECT_MAPPER.readTree(request.body()).path("stream").asBoolean());
        assertEquals("session-2", OBJECT_MAPPER.readTree(request.body()).path("end_user_id").asText());
        assertEquals("Bearer secret", request.headers().get("Authorization"));
    }

    @Test
    void openAiCompatibleBuildsTencentYuanqiPayloadWhenAdapterIsYuanqi() throws Exception {
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter();

        ProviderProtocolRequest request = adapter.buildRequest(
                "https://lke.cloud.tencent.com/v1/qbot/chat/sse",
                Map.of("input", "hello", "session_id", "session-3"),
                Map.of(
                        "x_adapter_id", "tencent_yuanqi",
                        "transformProfile", "tencent_yuanqi_agent",
                        "upstreamAgentId", "bot-app-key-123"),
                "ignored-secret",
                "trace-8");

        assertEquals("hello", OBJECT_MAPPER.readTree(request.body()).path("content").asText());
        assertEquals("session-3", OBJECT_MAPPER.readTree(request.body()).path("session_id").asText());
        assertEquals("session-3", OBJECT_MAPPER.readTree(request.body()).path("visitor_biz_id").asText());
        assertEquals("bot-app-key-123", OBJECT_MAPPER.readTree(request.body()).path("bot_app_key").asText());
    }

    @Test
    void platformSupportExtractsTencentYuanqiReplyTextFromSseStream() {
        String text = AgentPlatformAdapterSupport.extractResponseText(
                Map.of("x_adapter_id", "tencent_yuanqi"),
                """
                        event: reply
                        data: {"payload":{"content":"第一段"}}

                        event: reply
                        data: {"payload":{"content":"第二段"}}

                        data: [DONE]
                        """);

        assertEquals("第一段\n第二段", text);
    }
    @Test
    void platformSupportExtractsDifyReplyTextFromSseStream() {
        String text = AgentPlatformAdapterSupport.extractResponseText(
                Map.of("x_adapter_id", "dify"),
                """
                        data: {"event":"agent_thought","thought":""}

                        data: {"event":"agent_message","answer":"Hello"}

                        data: {"event":"agent_message","answer":" world"}

                        data: [DONE]
                        """);

        assertEquals("Hello world", text);
    }

    @Test
    void platformSupportSuggestsRelaxedHealthDefaultsForDify() {
        Map<String, Object> probeConfig = AgentPlatformAdapterSupport.suggestedProbeConfig(
                Map.of("x_adapter_id", "dify"),
                "https://api.dify.ai/v1/chat-messages",
                "openai_compatible",
                null,
                "dify_agent_app").orElseThrow();

        int timeoutSec = AgentPlatformAdapterSupport.suggestedTimeoutSec(
                Map.of("x_adapter_id", "dify"),
                "https://api.dify.ai/v1/chat-messages",
                "openai_compatible",
                null,
                "dify_agent_app").orElseThrow();

        assertEquals(45_000L, ((Number) probeConfig.get("latencyThresholdMs")).longValue());
        assertEquals(45, timeoutSec);
    }
}
