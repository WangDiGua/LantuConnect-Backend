package com.lantu.connect.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量驳回：id 列表 + 统一原因（与单条 reject 原因策略一致）。
 */
@Data
public class IdsWithReasonRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;

    @NotBlank
    private String reason;
}
