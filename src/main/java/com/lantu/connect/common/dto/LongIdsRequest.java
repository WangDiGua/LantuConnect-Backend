package com.lantu.connect.common.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量操作：数值主键列表（与前端 {@code ids: number[]} 对齐），单请求上限防刷。
 */
@Data
public class LongIdsRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;
}
