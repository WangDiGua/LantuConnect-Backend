package com.lantu.connect.integrationpackage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户侧：本人套餐摘要（不含明细项）；下拉绑定 Key 时仅选用 status=active */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationPackageOptionVO {

    private String id;

    private String name;

    private String description;

    /** active | disabled */
    private String status;

    private int itemCount;
}
