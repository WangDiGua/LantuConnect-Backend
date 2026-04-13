package com.lantu.connect.gateway.controller;

import com.lantu.connect.gateway.service.OpenAiCompatService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

@RestController
@RequestMapping("/openai/v1")
@RequiredArgsConstructor
public class OpenAiCompatController {

    private final OpenAiCompatService openAiCompatService;

    @GetMapping("/models")
    public Map<String, Object> models(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        return openAiCompatService.models(apiKey);
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
                                             @RequestBody Map<String, Object> body) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        if (openAiCompatService.isStreamRequested(body)) {
            StreamingResponseBody stream = outputStream -> openAiCompatService.chatCompletionsStream(apiKey, body, outputStream);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Accel-Buffering", "no")
                    .body(stream);
        }
        return ResponseEntity.ok(openAiCompatService.chatCompletions(apiKey, body));
    }

    @PostMapping("/responses")
    public Map<String, Object> responses(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
                                         @RequestBody Map<String, Object> body) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        return openAiCompatService.responses(apiKey, body);
    }

    @PostMapping("/assistants")
    public Map<String, Object> assistants(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
                                          @RequestBody Map<String, Object> body) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        return openAiCompatService.createAssistant(apiKey, body);
    }

    @PostMapping("/threads")
    public Map<String, Object> threads(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
                                       @RequestBody(required = false) Map<String, Object> body) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        return openAiCompatService.createThread(apiKey, body == null ? Map.of() : body);
    }

    @PostMapping("/threads/{threadId}/messages")
    public Map<String, Object> threadMessages(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
                                              @PathVariable String threadId,
                                              @RequestBody Map<String, Object> body) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        return openAiCompatService.createThreadMessage(apiKey, threadId, body);
    }

    @PostMapping("/threads/{threadId}/runs")
    public Map<String, Object> threadRuns(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
                                          @PathVariable String threadId,
                                          @RequestBody Map<String, Object> body) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        return openAiCompatService.createThreadRun(apiKey, threadId, body);
    }

    @GetMapping("/threads/{threadId}/runs/{runId}")
    public Map<String, Object> getThreadRun(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
                                            @PathVariable String threadId,
                                            @PathVariable String runId) {
        ApiKey apiKey = openAiCompatService.authenticate(authorization, apiKeyHeader);
        return openAiCompatService.getThreadRun(apiKey, threadId, runId);
    }
}
