package com.lantu.connect.gateway.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 以 JSON 提交技能包字节（Base64），便于仅支持 JSON 的网关/代理或与 multipart 二选一的前端。
 * 与 {@link ResourceRegistryController#uploadSkillPackage} 能力一致。
 */
@Data
public class SkillPackJsonUploadRequest {

    /**
     * Base64 本体；可带 data URL 前缀 {@code data:application/zip;base64,...}。
     * 兼容字段名 {@code file}。
     */
    @NotBlank(message = "file / fileBase64 不能为空")
    @JsonAlias("file")
    private String fileBase64;

    @Size(max = 512)
    @JsonAlias("originalFilename")
    private String filename;

    private Long resourceId;
}
