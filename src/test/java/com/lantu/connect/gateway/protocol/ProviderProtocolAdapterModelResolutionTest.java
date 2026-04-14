package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
