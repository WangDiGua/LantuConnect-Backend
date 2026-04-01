package com.lantu.connect.gateway.skillsmp;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.catalog.SkillCatalogOutboundRestTemplateFactory;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.skillsmp.dto.SkillsMpKeywordSearchResponse;
import com.lantu.connect.gateway.skillsmp.dto.SkillsMpSkillJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * SkillsMP 公开 REST API（需 API Key）：关键词搜索技能列表。
 *
 * @see <a href="https://skillsmp.com/docs/api">SkillsMP API</a>
 */
@Component
@Slf4j
public class SkillsMpCatalogClient {

    public List<SkillsMpSkillJson> searchPage(SkillExternalCatalogProperties catalogProperties,
                                             String query, int page, int limit, String sortBy) {
        SkillExternalCatalogProperties.SkillsMp cfg = catalogProperties.getSkillsmp();
        if (cfg == null || !cfg.isEnabled()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "SkillsMP 已在配置中关闭（skillsmp.enabled=false）");
        }
        String key = cfg.getApiKey();
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "未配置 SkillsMP API Key，请设置环境变量 SKILLSMP_API_KEY 或 lantu.skill-external-catalog.skillsmp.api-key");
        }
        String base = StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl().trim() : "https://skillsmp.com/api/v1";
        String url = base.endsWith("/") ? base + "skills/search" : base + "/skills/search";
        String uri = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("q", query)
                .queryParam("page", page)
                .queryParam("limit", Math.min(100, Math.max(1, limit)))
                .queryParam("sortBy", StringUtils.hasText(sortBy) ? sortBy : "stars")
                .encode(StandardCharsets.UTF_8)
                .build(true)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + key.trim());
        headers.set(HttpHeaders.ACCEPT, "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = SkillCatalogOutboundRestTemplateFactory.create(catalogProperties.getOutboundHttpProxy());
        try {
            ResponseEntity<SkillsMpKeywordSearchResponse> resp = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, SkillsMpKeywordSearchResponse.class);
            SkillsMpKeywordSearchResponse body = resp.getBody();
            if (body == null) {
                return Collections.emptyList();
            }
            if (Boolean.FALSE.equals(body.getSuccess()) && body.getError() != null) {
                throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                        "SkillsMP: " + body.getError().getCode() + " — " + body.getError().getMessage());
            }
            if (body.getData() == null || body.getData().getSkills() == null) {
                return Collections.emptyList();
            }
            return body.getData().getSkills();
        } catch (HttpStatusCodeException e) {
            log.warn("SkillsMP HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "SkillsMP API Key 无效或未授权");
            }
            if (e.getStatusCode().value() == 429) {
                throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR, "SkillsMP 当日配额已用尽，请明日再试");
            }
            throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                    "SkillsMP 请求失败 HTTP " + e.getStatusCode().value());
        }
    }
}
