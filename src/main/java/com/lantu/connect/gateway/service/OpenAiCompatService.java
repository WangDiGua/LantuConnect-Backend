package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OpenAiCompatService {

    private static final String OWNER_TYPE_AGENT = "agent";
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final UnifiedGatewayService unifiedGatewayService;
    private final ApiKeyScopeService apiKeyScopeService;
    private final ObjectMapper objectMapper;

    public ApiKey authenticate(String authorization, String xApiKey) {
        String raw = StringUtils.hasText(authorization) ? authorization : xApiKey;
        ApiKey key = apiKeyScopeService.authenticateOrNull(raw);
        if (key == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "需要有效的 Bearer nx-sk 或 X-Api-Key");
        }
        return key;
    }

    public boolean isStreamRequested(Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        Object raw = body.get("stream");
        if (raw instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    public Map<String, Object> models(ApiKey apiKey) {
        List<Map<String, Object>> rows;
        if (OWNER_TYPE_AGENT.equalsIgnoreCase(apiKey.getOwnerType())) {
            rows = jdbcTemplate.queryForList("""
                    SELECT ae.model_alias AS model_alias
                    FROM t_resource_agent_ext ae
                    JOIN t_resource r ON r.id = ae.resource_id
                    WHERE r.deleted = 0
                      AND r.resource_type = 'agent'
                      AND r.id = ?
                      AND r.status = 'published'
                      AND IFNULL(ae.enabled, 1) = 1
                    """, apiKey.getOwnerId());
        } else {
            rows = jdbcTemplate.queryForList("""
                    SELECT ae.model_alias AS model_alias
                    FROM t_resource_agent_ext ae
                    JOIN t_resource r ON r.id = ae.resource_id
                    WHERE r.deleted = 0
                      AND r.resource_type = 'agent'
                      AND r.status = 'published'
                      AND IFNULL(ae.enabled, 1) = 1
                    ORDER BY ae.model_alias
                    """);
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String model = str(row.get("model_alias"));
            if (!StringUtils.hasText(model)) {
                continue;
            }
            data.add(Map.of(
                    "id", model,
                    "object", "model",
                    "created", Instant.now().getEpochSecond(),
                    "owned_by", "nexus"));
        }
        return Map.of("object", "list", "data", data);
    }

    public Map<String, Object> chatCompletions(ApiKey apiKey, Map<String, Object> body) {
        ChatCompletionResult result = runChatCompletion(apiKey, body);
        return Map.of(
                "id", result.id(),
                "object", "chat.completion",
                "created", result.createdAt(),
                "model", result.model(),
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", result.text()),
                        "finish_reason", "stop"
                )));
    }

    public void chatCompletionsStream(ApiKey apiKey, Map<String, Object> body, OutputStream outputStream) throws IOException {
        ChatCompletionResult result = runChatCompletion(apiKey, body);
        // 极简 SSE：先推送完整文本块，再推送 stop 块，最后 [DONE]
        Map<String, Object> chunk1 = Map.of(
                "id", result.id(),
                "object", "chat.completion.chunk",
                "created", result.createdAt(),
                "model", result.model(),
                "choices", List.of(Map.of(
                        "index", 0,
                        "delta", Map.of("role", "assistant", "content", result.text()),
                        "finish_reason", null
                )));
        Map<String, Object> chunk2 = Map.of(
                "id", result.id(),
                "object", "chat.completion.chunk",
                "created", result.createdAt(),
                "model", result.model(),
                "choices", List.of(Map.of(
                        "index", 0,
                        "delta", Map.of(),
                        "finish_reason", "stop"
                )));
        writeSse(outputStream, chunk1);
        writeSse(outputStream, chunk2);
        outputStream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    public Map<String, Object> responses(ApiKey apiKey, Map<String, Object> body) {
        String model = requiredModel(body);
        Long agentId = resolveAgentIdByModelAlias(apiKey, model);
        String input = flattenResponsesInput(body == null ? null : body.get("input"));
        String text = invokeAgent(apiKey, agentId, input);
        String id = "resp_" + UUID.randomUUID().toString().replace("-", "");
        return Map.of(
                "id", id,
                "object", "response",
                "created", Instant.now().getEpochSecond(),
                "model", model,
                "output_text", text,
                "output", List.of(Map.of("type", "message", "role", "assistant", "content", List.of(Map.of("type", "output_text", "text", text))))
        );
    }

    public Map<String, Object> createAssistant(ApiKey apiKey, Map<String, Object> body) {
        String model = requiredModel(body);
        resolveAgentIdByModelAlias(apiKey, model);
        String id = "asst_" + UUID.randomUUID().toString().replace("-", "");
        String ownerType = normalizeOwnerType(apiKey);
        String ownerId = normalizeOwnerId(apiKey);
        long now = Instant.now().getEpochSecond();
        String name = strOrDefault(body.get("name"), "Nexus Assistant");
        String instructions = strOrDefault(body.get("instructions"), "");
        jdbcTemplate.update("""
                INSERT INTO t_openai_assistant_state(id, owner_type, owner_id, model_alias, name, instructions, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, ownerType, ownerId, model, name, instructions, now, now);
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("id", id);
        assistant.put("object", "assistant");
        assistant.put("created_at", now);
        assistant.put("model", model);
        assistant.put("name", name);
        assistant.put("instructions", instructions);
        return assistant;
    }

    public Map<String, Object> createThread(ApiKey apiKey, Map<String, Object> body) {
        String id = "thread_" + UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();
        jdbcTemplate.update("""
                INSERT INTO t_openai_thread_state(id, owner_type, owner_id, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?)
                """,
                id, normalizeOwnerType(apiKey), normalizeOwnerId(apiKey), now, now);
        return Map.of("id", id, "object", "thread", "created_at", now);
    }

    public Map<String, Object> createThreadMessage(ApiKey apiKey, String threadId, Map<String, Object> body) {
        requireThreadOwnedByApiKey(apiKey, threadId);
        String role = strOrDefault(body.get("role"), "user");
        MessageContent messageContent = normalizeMessageContent(body.get("content"));
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();
        jdbcTemplate.update("""
                INSERT INTO t_openai_thread_message_state(id, thread_id, role, content_text, content_json, created_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """,
                messageId, threadId, role, messageContent.text(), toJson(messageContent.contentNodes()), now);
        jdbcTemplate.update("UPDATE t_openai_thread_state SET updated_at = ? WHERE id = ?", now, threadId);
        return Map.of(
                "id", messageId,
                "object", "thread.message",
                "created_at", now,
                "role", role,
                "content", messageContent.contentNodes());
    }

    public Map<String, Object> createThreadRun(ApiKey apiKey, String threadId, Map<String, Object> body) {
        requireThreadOwnedByApiKey(apiKey, threadId);
        String assistantId = str(body.get("assistant_id"));
        if (!StringUtils.hasText(assistantId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "assistant_id 不能为空");
        }
        Map<String, Object> assistant = loadAssistantOwnedByApiKey(apiKey, assistantId);
        String model = str(assistant.get("model_alias"));
        Long agentId = resolveAgentIdByModelAlias(apiKey, model);

        List<Map<String, Object>> userMessages = jdbcTemplate.queryForList("""
                SELECT content_text
                FROM t_openai_thread_message_state
                WHERE thread_id = ? AND role = 'user'
                ORDER BY created_at ASC, id ASC
                """, threadId);
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : userMessages) {
            String line = str(msg.get("content_text"));
            if (StringUtils.hasText(line)) {
                sb.append(line).append('\n');
            }
        }
        String answer = invokeAgent(apiKey, agentId, sb.toString().trim());

        // 写入 assistant 消息
        String assistantMsgId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();
        List<Map<String, Object>> answerNodes = List.of(Map.of("type", "text", "text", Map.of("value", answer)));
        jdbcTemplate.update("""
                INSERT INTO t_openai_thread_message_state(id, thread_id, role, content_text, content_json, created_at)
                VALUES(?, ?, 'assistant', ?, ?, ?)
                """,
                assistantMsgId, threadId, answer, toJson(answerNodes), now);

        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO t_openai_thread_run_state(id, thread_id, assistant_id, owner_type, owner_id, model_alias, status, output_text, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, 'completed', ?, ?, ?)
                """,
                runId,
                threadId,
                assistantId,
                normalizeOwnerType(apiKey),
                normalizeOwnerId(apiKey),
                model,
                answer,
                now,
                now);
        jdbcTemplate.update("UPDATE t_openai_thread_state SET updated_at = ? WHERE id = ?", now, threadId);

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", runId);
        run.put("object", "thread.run");
        run.put("thread_id", threadId);
        run.put("assistant_id", assistantId);
        run.put("status", "completed");
        run.put("created_at", now);
        return run;
    }

    public Map<String, Object> getThreadRun(ApiKey apiKey, String threadId, String runId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, thread_id, assistant_id, status, created_at
                FROM t_openai_thread_run_state
                WHERE id = ? AND thread_id = ? AND owner_type = ? AND owner_id = ?
                LIMIT 1
                """,
                runId, threadId, normalizeOwnerType(apiKey), normalizeOwnerId(apiKey));
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "run 不存在");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", str(row.get("id")));
        run.put("object", "thread.run");
        run.put("thread_id", str(row.get("thread_id")));
        run.put("assistant_id", str(row.get("assistant_id")));
        run.put("status", strOrDefault(row.get("status"), "completed"));
        run.put("created_at", longValue(row.get("created_at")));
        return run;
    }

    private ChatCompletionResult runChatCompletion(ApiKey apiKey, Map<String, Object> body) {
        String model = requiredModel(body);
        Long agentId = resolveAgentIdByModelAlias(apiKey, model);
        String query = flattenMessages(body.get("messages"));
        String text = invokeAgent(apiKey, agentId, query);
        String id = "chatcmpl_" + UUID.randomUUID().toString().replace("-", "");
        long createdAt = Instant.now().getEpochSecond();
        return new ChatCompletionResult(id, model, text, createdAt);
    }

    private void writeSse(OutputStream outputStream, Map<String, Object> payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload);
        outputStream.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void requireThreadOwnedByApiKey(ApiKey apiKey, String threadId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id
                FROM t_openai_thread_state
                WHERE id = ? AND owner_type = ? AND owner_id = ?
                LIMIT 1
                """,
                threadId, normalizeOwnerType(apiKey), normalizeOwnerId(apiKey));
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "thread 不存在");
        }
    }

    private Map<String, Object> loadAssistantOwnedByApiKey(ApiKey apiKey, String assistantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, model_alias, name, instructions, created_at
                FROM t_openai_assistant_state
                WHERE id = ? AND owner_type = ? AND owner_id = ?
                LIMIT 1
                """,
                assistantId, normalizeOwnerType(apiKey), normalizeOwnerId(apiKey));
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "assistant 不存在");
        }
        return rows.get(0);
    }

    private String requiredModel(Map<String, Object> body) {
        Object model = body == null ? null : body.get("model");
        String value = model == null ? "" : String.valueOf(model).trim();
        if (!StringUtils.hasText(value)) {
            model = body == null ? null : body.get("customized_model_id");
        }
        value = model == null ? "" : String.valueOf(model).trim();
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "model 不能为空");
        }
        return value;
    }

    private String flattenResponsesInput(Object rawInput) {
        if (rawInput == null) {
            return "";
        }
        if (rawInput instanceof String s) {
            return s;
        }
        if (rawInput instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    String text = String.valueOf(item);
                    if (StringUtils.hasText(text)) {
                        sb.append(text).append('\n');
                    }
                    continue;
                }
                String role = str(map.get("role"));
                if (!"user".equalsIgnoreCase(role)) {
                    continue;
                }
                String line = extractResponsesContent(map.get("content"));
                if (StringUtils.hasText(line)) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString().trim();
        }
        return extractResponsesContent(rawInput);
    }

    private String extractResponsesContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object node : list) {
                if (node instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text instanceof String s && StringUtils.hasText(s)) {
                        sb.append(s);
                        continue;
                    }
                    Object nestedValue = null;
                    if (text instanceof Map<?, ?> nested) {
                        nestedValue = nested.get("value");
                    }
                    if (nestedValue != null) {
                        sb.append(String.valueOf(nestedValue));
                        continue;
                    }
                    Object inputText = map.get("input_text");
                    if (inputText instanceof String s && StringUtils.hasText(s)) {
                        sb.append(s);
                        continue;
                    }
                    sb.append(extractResponsesContent(map));
                } else if (node != null) {
                    sb.append(node);
                }
            }
            return sb.toString();
        }
        if (content instanceof Map<?, ?> map) {
            Object prompt = map.get("prompt");
            if (prompt instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
            Object text = map.get("text");
            if (text instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
            Object inputText = map.get("input_text");
            if (inputText instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
            Object parts = map.get("parts");
            if (parts instanceof List<?> list) {
                return extractResponsesContent(list);
            }
            Object messages = map.get("messages");
            if (messages instanceof List<?> list) {
                return flattenResponsesInput(list);
            }
        }
        return String.valueOf(content);
    }

    private Long resolveAgentIdByModelAlias(ApiKey apiKey, String modelAlias) {
        List<Map<String, Object>> rows;
        if (OWNER_TYPE_AGENT.equalsIgnoreCase(apiKey.getOwnerType())) {
            rows = jdbcTemplate.queryForList("""
                    SELECT r.id
                    FROM t_resource r
                    JOIN t_resource_agent_ext ae ON ae.resource_id = r.id
                    WHERE r.deleted = 0
                      AND r.resource_type = 'agent'
                      AND r.status = 'published'
                      AND IFNULL(ae.enabled, 1) = 1
                      AND ae.model_alias = ?
                      AND r.id = ?
                    LIMIT 1
                    """, modelAlias, apiKey.getOwnerId());
        } else {
            rows = jdbcTemplate.queryForList("""
                    SELECT r.id
                    FROM t_resource r
                    JOIN t_resource_agent_ext ae ON ae.resource_id = r.id
                    WHERE r.deleted = 0
                      AND r.resource_type = 'agent'
                      AND r.status = 'published'
                      AND IFNULL(ae.enabled, 1) = 1
                      AND ae.model_alias = ?
                    LIMIT 1
                    """, modelAlias);
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "model 不存在或不可访问: " + modelAlias);
        }
        Number n = (Number) rows.get(0).get("id");
        return n.longValue();
    }

    private String flattenMessages(Object rawMessages) {
        if (rawMessages == null) {
            return "";
        }
        try {
            List<Map<String, Object>> list = objectMapper.convertValue(rawMessages, LIST_OF_MAP);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> msg : list) {
                if (!"user".equalsIgnoreCase(str(msg.get("role")))) {
                    continue;
                }
                String line = extractUserMessageText(msg.get("content"));
                if (StringUtils.hasText(line)) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString().trim();
        } catch (IllegalArgumentException ex) {
            return String.valueOf(rawMessages);
        }
    }

    private String extractUserMessageText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        try {
            List<Map<String, Object>> nodes = objectMapper.convertValue(content, LIST_OF_MAP);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> node : nodes) {
                Object text = node.get("text");
                if (text instanceof String s) {
                    sb.append(s);
                } else if (text instanceof Map<?, ?> map && map.get("value") != null) {
                    sb.append(String.valueOf(map.get("value")));
                }
            }
            return sb.toString();
        } catch (IllegalArgumentException ex) {
            return String.valueOf(content);
        }
    }

    private MessageContent normalizeMessageContent(Object rawContent) {
        if (rawContent == null) {
            return new MessageContent("", List.of(Map.of("type", "text", "text", Map.of("value", ""))));
        }
        if (rawContent instanceof String s) {
            return new MessageContent(s, List.of(Map.of("type", "text", "text", Map.of("value", s))));
        }
        try {
            List<Map<String, Object>> nodes = objectMapper.convertValue(rawContent, LIST_OF_MAP);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> node : nodes) {
                Object text = node.get("text");
                if (text instanceof String s) {
                    sb.append(s);
                } else if (text instanceof Map<?, ?> map && map.get("value") != null) {
                    sb.append(String.valueOf(map.get("value")));
                }
            }
            return new MessageContent(sb.toString(), nodes.isEmpty()
                    ? List.of(Map.of("type", "text", "text", Map.of("value", "")))
                    : nodes);
        } catch (IllegalArgumentException ex) {
            String fallback = String.valueOf(rawContent);
            return new MessageContent(fallback, List.of(Map.of("type", "text", "text", Map.of("value", fallback))));
        }
    }

    private String invokeAgent(ApiKey apiKey, Long agentId, String query) {
        InvokeRequest request = new InvokeRequest();
        request.setResourceType("agent");
        request.setResourceId(String.valueOf(agentId));
        request.setPayload(Map.of("query", query));
        InvokeResponse resp = unifiedGatewayService.invoke(null, UUID.randomUUID().toString(), "0.0.0.0", request, apiKey);
        if (resp == null || !StringUtils.hasText(resp.getBody())) {
            return "";
        }
        try {
            JsonNodeHolder holder = objectMapper.readValue(resp.getBody(), JsonNodeHolder.class);
            if (StringUtils.hasText(holder.text)) {
                return holder.text;
            }
        } catch (Exception ignored) {
        }
        return resp.getBody();
    }

    private String normalizeOwnerType(ApiKey apiKey) {
        String t = apiKey == null ? null : apiKey.getOwnerType();
        return StringUtils.hasText(t) ? t.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    private String normalizeOwnerId(ApiKey apiKey) {
        String id = apiKey == null ? null : apiKey.getOwnerId();
        return StringUtils.hasText(id) ? id.trim() : "";
    }

    private long longValue(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String strOrDefault(Object v, String defaultValue) {
        String s = str(v);
        return StringUtils.hasText(s) ? s : defaultValue;
    }

    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private record ChatCompletionResult(String id, String model, String text, long createdAt) {
    }

    private record MessageContent(String text, List<Map<String, Object>> contentNodes) {
    }

    private static class JsonNodeHolder {
        public String text;
    }
}
