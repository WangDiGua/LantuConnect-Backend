package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.config.HostedLlmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 执行 Hosted Skill：读 {@code t_resource_skill_ext}，调用 OpenAI 兼容 chat completions。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HostedSkillExecutionService {

    public static final String EXECUTION_HOSTED = "hosted";
    public static final String INVOKETYPE_HOSTED_LLM = "hosted_llm";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HostedLlmProperties hostedLlmProperties;

    /**
     * 对 JSON 文本做前置归一化（仅 hosted skill）；失败抛出业务异常。
     */
    public String normalizeJsonWithHostedSkill(Long skillResourceId, String jsonInput) {
        return runCompletion(skillResourceId, jsonInput == null ? "" : jsonInput, true);
    }

    /**
     * Invoke 用：payload 整体序列化后作为用户输入。
     */
    public String invokeSkill(Long skillResourceId, Map<String, Object> invokePayload) {
        try {
            String json = invokePayload == null || invokePayload.isEmpty()
                    ? "{}"
                    : objectMapper.writeValueAsString(invokePayload);
            return runCompletion(skillResourceId, json, false);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Hosted Skill 入参序列化失败: " + e.getMessage());
        }
    }

    private String runCompletion(Long skillResourceId, String inputText, boolean strictJsonOutput) {
        if (!hostedLlmProperties.isEnabled()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Hosted LLM 未启用（lantu.hosted-llm.enabled=false）");
        }
        if (!StringUtils.hasText(hostedLlmProperties.getBaseUrl()) || !StringUtils.hasText(hostedLlmProperties.getApiKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Hosted LLM 未配置 baseUrl 或 apiKey");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT r.status, e.execution_mode, e.hosted_system_prompt, e.hosted_user_template,
                               e.hosted_default_model, e.hosted_temperature
                        FROM t_resource_skill_ext e
                        INNER JOIN t_resource r ON r.id = e.resource_id AND r.deleted = 0
                        WHERE e.resource_id = ?
                        LIMIT 1
                        """,
                skillResourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Skill 资源不存在");
        }
        Map<String, Object> row = rows.get(0);
        String status = row.get("status") == null ? "" : String.valueOf(row.get("status"));
        if (!"published".equalsIgnoreCase(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Skill 未发布，禁止调用");
        }
        String mode = row.get("execution_mode") == null ? EXECUTION_HOSTED : String.valueOf(row.get("execution_mode")).trim().toLowerCase(Locale.ROOT);
        if (!EXECUTION_HOSTED.equals(mode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该 Skill 非 hosted 模式");
        }
        String system = row.get("hosted_system_prompt") == null ? null : String.valueOf(row.get("hosted_system_prompt"));
        if (!StringUtils.hasText(system)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Hosted Skill 未配置 hostedSystemPrompt");
        }
        String template = row.get("hosted_user_template") == null ? "" : String.valueOf(row.get("hosted_user_template"));
        String userMsg = StringUtils.hasText(template)
                ? template.replace("{{input}}", inputText).replace("{input}", inputText)
                : inputText;
        String model = row.get("hosted_default_model") == null ? null : String.valueOf(row.get("hosted_default_model")).trim();
        if (!StringUtils.hasText(model)) {
            model = hostedLlmProperties.getDefaultModel();
        }
        Double temp = null;
        if (row.get("hosted_temperature") != null) {
            try {
                temp = Double.valueOf(String.valueOf(row.get("hosted_temperature")));
            } catch (Exception ignored) {
            }
        }

        try {
            String body = buildChatRequestBody(model, system, userMsg, temp);
            String url = hostedLlmProperties.getBaseUrl().trim().replaceAll("/+$", "") + "/chat/completions";
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1, hostedLlmProperties.getConnectTimeoutSeconds())))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, hostedLlmProperties.getReadTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + hostedLlmProperties.getApiKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("hosted skill llm http status={} body_preview={}", resp.statusCode(),
                        resp.body() == null ? "" : resp.body().substring(0, Math.min(500, resp.body().length())));
                throw new BusinessException(ResultCode.PARAM_ERROR, "大模型调用失败 HTTP " + resp.statusCode());
            }
            String content = extractAssistantContent(resp.body());
            if (strictJsonOutput) {
                validateJson(content);
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("hosted skill llm error skillId={}", skillResourceId, e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "大模型调用异常: " + e.getMessage());
        }
    }

    private void validateJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "归一化结果为空");
        }
        String trimmed = content.trim();
        try {
            objectMapper.readTree(trimmed);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "前置 Skill 输出非法 JSON，将中止 MCP 调用: " + e.getMessage());
        }
    }

    private String buildChatRequestBody(String model, String system, String user, Double temperature) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", model);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        );
        root.put("messages", messages);
        if (temperature != null) {
            root.put("temperature", temperature);
        }
        return objectMapper.writeValueAsString(root);
    }

    private String extractAssistantContent(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "大模型响应无 choices");
        }
        JsonNode msg = choices.get(0).get("message");
        if (msg == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "大模型响应无 message");
        }
        JsonNode c = msg.get("content");
        if (c == null || c.isNull()) {
            return "";
        }
        return c.asText("");
    }
}
