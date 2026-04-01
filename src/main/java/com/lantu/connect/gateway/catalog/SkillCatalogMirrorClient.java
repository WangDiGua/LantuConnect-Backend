package com.lantu.connect.gateway.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 从自建 HTTPS JSON 拉取技能市场条目（国内 OSS/CDN），作为 SkillsMP 失败时的回退。
 */
@Component
@Slf4j
public class SkillCatalogMirrorClient {

    private final ObjectMapper objectMapper;

    public SkillCatalogMirrorClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SkillExternalCatalogItemVO> fetchList(String urlRaw,
                                                     SkillExternalCatalogProperties.OutboundHttpProxy outboundHttpProxy) {
        if (!StringUtils.hasText(urlRaw)) {
            return List.of();
        }
        String url = urlRaw.trim();
        RestTemplate restTemplate = SkillCatalogOutboundRestTemplateFactory.create(outboundHttpProxy);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                        "镜像目录 HTTP " + resp.getStatusCode().value());
            }
            return parseBody(resp.getBody());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("拉取技能镜像目录失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                    "镜像目录不可用: " + e.getMessage());
        }
    }

    private List<SkillExternalCatalogItemVO> parseBody(String json) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode array;
        if (root.isArray()) {
            array = root;
        } else if (root.has("data") && root.get("data").isArray()) {
            array = root.get("data");
        } else {
            return List.of();
        }
        List<SkillExternalCatalogItemVO> parsed = objectMapper.convertValue(
                array,
                new TypeReference<List<SkillExternalCatalogItemVO>>() { });
        if (parsed == null) {
            return List.of();
        }
        return parsed.stream()
                .filter(v -> v != null
                        && StringUtils.hasText(v.getId())
                        && StringUtils.hasText(v.getName())
                        && StringUtils.hasText(v.getPackUrl()))
                .collect(Collectors.toList());
    }
}
