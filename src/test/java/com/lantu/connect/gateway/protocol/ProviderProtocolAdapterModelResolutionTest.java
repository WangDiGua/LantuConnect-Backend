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
}
