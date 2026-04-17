package com.lantu.connect.gateway.capability;

import com.lantu.connect.gateway.capability.dto.CapabilityImportRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityImportSuggestionVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityImportDetectorTest {

    private final CapabilityImportDetector detector = new CapabilityImportDetector();

    @Test
    void shouldDetectOpenAiCompatibleAgentFromEndpoint() {
        CapabilityImportRequest request = new CapabilityImportRequest();
        request.setSource("https://api.openai.com/v1/responses");

        CapabilityImportSuggestionVO suggestion = detector.detect(request);

        assertEquals("agent", suggestion.getDetectedType());
        assertEquals("remote_agent", suggestion.getRuntimeMode());
        assertEquals("openai_compatible", suggestion.getCapabilities().get("registrationProtocol"));
        assertEquals("https://api.openai.com/v1/responses", suggestion.getDefaults().get("endpoint"));
    }

    @Test
    void shouldDetectMcpFromWebsocketEndpoint() {
        CapabilityImportRequest request = new CapabilityImportRequest();
        request.setSource("wss://example.com/mcp");

        CapabilityImportSuggestionVO suggestion = detector.detect(request);

        assertEquals("mcp", suggestion.getDetectedType());
        assertEquals("mcp_websocket", suggestion.getRuntimeMode());
        assertEquals("wss://example.com/mcp", suggestion.getDefaults().get("endpoint"));
    }

    @Test
    void shouldExposeDeepSeekAsOpenAiFamilyProviderPreset() {
        CapabilityImportRequest request = new CapabilityImportRequest();
        request.setSource("https://api.deepseek.com/v1/chat/completions");

        CapabilityImportSuggestionVO suggestion = detector.detect(request);

        assertEquals("agent", suggestion.getDetectedType());
        assertEquals("openai_compatible", suggestion.getCapabilities().get("registrationProtocol"));
        assertEquals("deepseek", suggestion.getCapabilities().get("providerPreset"));
    }

    @Test
    void shouldDetectPromptSkillFromPlainText() {
        CapabilityImportRequest request = new CapabilityImportRequest();
        request.setSource("""
                你是一名 PPT 生成助理。
                请根据用户提供的大纲输出结构化页面清单，并在需要时建议调用远程工具。
                """);

        CapabilityImportSuggestionVO suggestion = detector.detect(request);

        assertEquals("skill", suggestion.getDetectedType());
        assertEquals("prompt_context", suggestion.getRuntimeMode());
        assertNotNull(suggestion.getDefaults());
        assertTrue(String.valueOf(suggestion.getDefaults().get("contextPrompt")).contains("PPT"));
    }
}
