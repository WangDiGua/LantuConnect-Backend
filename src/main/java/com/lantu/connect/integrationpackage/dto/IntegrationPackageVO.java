package com.lantu.connect.integrationpackage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationPackageVO {

    private String id;

    private String name;

    private String description;

    private String status;

    private String createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<IntegrationPackageItemDTO> items;
}
