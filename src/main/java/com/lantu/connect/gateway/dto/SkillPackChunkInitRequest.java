package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 技能包分片上传：初始化会话。
 */
@Data
public class SkillPackChunkInitRequest {

    @NotBlank(message = "fileName 不能为空")
    @Size(max = 512)
    private String fileName;

    /** 完整文件大小（字节） */
    @Min(value = 1, message = "fileSize 须大于 0")
    private long fileSize;

    private Long resourceId;

    @Size(max = 512)
    private String skillRoot;
}
