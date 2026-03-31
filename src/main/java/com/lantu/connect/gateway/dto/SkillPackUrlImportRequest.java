package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 从可访问 URL 拉取 Anthropic 式技能 zip 并入库（与同文件上传共用校验与存储逻辑）。
 */
@Data
public class SkillPackUrlImportRequest {

    @NotBlank(message = "url 不能为空")
    @Size(max = 2048, message = "url 过长")
    private String url;

    /**
     * 已存在的 skill 资源 id 时，拉包后覆盖该资源制品（须可编辑状态）。
     */
    private Long resourceId;
}
