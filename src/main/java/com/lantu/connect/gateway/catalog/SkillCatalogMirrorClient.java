package com.lantu.connect.gateway.catalog;



import com.lantu.connect.common.exception.BusinessException;

import com.lantu.connect.common.result.ResultCode;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;

import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpMethod;

import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Component;

import org.springframework.util.StringUtils;

import org.springframework.web.client.RestTemplate;



import java.util.ArrayList;

import java.util.List;

import java.util.Locale;

import java.util.Optional;



/**

 * 从 HTTPS JSON 拉取技能市场条目（自建 OSS/CDN 或第三方目录 API），作为 SkillsMP 失败时的回退；可多 URL 由上层循环合并。

 * 支持根数组、{@code data}、{@code skills}；若响应含 {@code pagination.hasNext} 则按 {@code page} 查询参数续拉（如 skill0.atypica.ai）。

 */

@Component

@Slf4j

public class SkillCatalogMirrorClient {



    private static final int MAX_PAGINATION_PAGES = 200;



    private final ObjectMapper objectMapper;



    public SkillCatalogMirrorClient(ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;

    }



    public List<SkillExternalCatalogItemVO> fetchList(String urlRaw,

                                                     SkillExternalCatalogProperties.OutboundHttpProxy outboundHttpProxy) {

        return fetchList(urlRaw, "AUTO", outboundHttpProxy);

    }



    /**

     * @param formatRaw AUTO / SKILL0（等价）；其它取值暂时亦走同一 JSON 解析链路。

     */

    public List<SkillExternalCatalogItemVO> fetchList(String urlRaw,

                                                     String formatRaw,

                                                     SkillExternalCatalogProperties.OutboundHttpProxy outboundHttpProxy) {

        if (!StringUtils.hasText(urlRaw)) {

            return List.of();

        }

        String fmt = normalizeFormat(formatRaw);

        if (!"AUTO".equals(fmt) && !"SKILL0".equals(fmt)) {

            log.debug("目录源 format={} 按 AUTO 解析: {}", formatRaw, urlRaw);

        }

        String templateUrl = urlRaw.trim();

        RestTemplate restTemplate = SkillCatalogOutboundRestTemplateFactory.create(outboundHttpProxy);

        try {

            List<SkillExternalCatalogItemVO> all = new ArrayList<>();

            String fetchUrl = templateUrl;

            for (int pageRound = 0; pageRound < MAX_PAGINATION_PAGES; pageRound++) {

                ResponseEntity<String> resp = restTemplate.exchange(

                        fetchUrl, HttpMethod.GET, null, String.class);

                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {

                    throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,

                            "镜像目录 HTTP " + resp.getStatusCode().value());

                }

                SkillCatalogMirrorResponseReader.ParsedPage page =

                        SkillCatalogMirrorResponseReader.parseFirstPage(

                                resp.getBody(), objectMapper, templateUrl);

                all.addAll(page.items());

                Optional<Integer> next = page.nextPageNumber();

                if (next.isEmpty()) {

                    break;

                }

                fetchUrl = SkillCatalogMirrorResponseReader.urlWithPageParam(templateUrl, next.get());

            }

            return all;

        } catch (BusinessException e) {

            throw e;

        } catch (Exception e) {

            log.warn("拉取技能镜像目录失败: {}", e.getMessage());

            throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,

                    "镜像目录不可用: " + e.getMessage());

        }

    }



    private static String normalizeFormat(String formatRaw) {

        if (!StringUtils.hasText(formatRaw)) {

            return "AUTO";

        }

        return formatRaw.trim().toUpperCase(Locale.ROOT);

    }

}

