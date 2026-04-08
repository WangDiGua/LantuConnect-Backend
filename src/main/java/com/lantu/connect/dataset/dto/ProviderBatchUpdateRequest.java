package com.lantu.connect.dataset.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * PUT /providers/batch：与单条 {@link ProviderUpdateRequest} 字段并列 + ids。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderBatchUpdateRequest extends ProviderUpdateRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;
}
