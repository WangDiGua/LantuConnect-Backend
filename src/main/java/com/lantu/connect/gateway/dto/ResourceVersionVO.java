package com.lantu.connect.gateway.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResourceVersionVO {

    private Long id;
    private Long resourceId;
    private String version;
    private String status;
    /** 与前端约定字段名 isCurrent；入参仍兼容 JSON 键 current。 */
    @JsonProperty("isCurrent")
    @JsonAlias("current")
    private Boolean current;
    private LocalDateTime createTime;
}

