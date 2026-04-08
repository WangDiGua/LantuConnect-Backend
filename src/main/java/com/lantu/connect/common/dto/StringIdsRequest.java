package com.lantu.connect.common.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量操作：字符串主键列表（API Key、Token、限流规则 id 等）。
 */
@Data
public class StringIdsRequest {

    @NotEmpty
    @Size(max = 200)
    private List<String> ids;
}
