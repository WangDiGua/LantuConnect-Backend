package com.lantu.connect.sysconfig.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统配置 QuotaCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class QuotaCreateRequest {

    @NotBlank
    @JsonAlias("targetType")
    private String subjectType;

    @NotBlank
    @JsonAlias("targetName")
    private String subjectName;

    /** 部门 / 用户主键，全局配额可不传 */
    @JsonAlias("targetId")
    private String subjectId;

    /** all 或 agent/skill/mcp/app/dataset，默认 all */
    private String resourceCategory;

    private Long dailyLimit;
    private Long monthlyLimit;
}
