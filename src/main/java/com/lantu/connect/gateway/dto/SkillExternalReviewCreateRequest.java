package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SkillExternalReviewCreateRequest {
    @NotBlank
    private String itemKey;

    private int rating;

    /** 可选；空串由服务层归一为 null */
    @Size(max = 4000)
    private String comment;
}
