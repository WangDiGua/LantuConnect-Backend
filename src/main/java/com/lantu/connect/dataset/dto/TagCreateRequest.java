package com.lantu.connect.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据集 TagCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class TagCreateRequest {

    @NotBlank
    private String name;

    /** 与库表 t_tag.category 对应；为空时服务端默认 general（与种子数据一致） */
    private String category;
}
