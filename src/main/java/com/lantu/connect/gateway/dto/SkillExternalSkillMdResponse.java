package com.lantu.connect.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部市场详情：服务端代拉 GitHub {@code SKILL.md}（raw）结果；非 GitHub 或拉取失败时 {@code markdown} 为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillExternalSkillMdResponse {

    /** raw 原文；无法解析或 GitHub 返回 404 等时为 null */
    private String markdown;

    /** 成功时最终命中的 raw 地址，便于排查与「在 GitHub 查看」 */
    private String resolvedRawUrl;

    /** 简短说明：非 GitHub、超时、正文过大截断等 */
    private String hint;

    private boolean truncated;

    private boolean fromCache;

    /** 正文来自目录镜像表预取的 GitHub raw，非本次实时请求 */
    private boolean fromDbMirror;
}
