package com.lantu.connect.compat.robotfactory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RobotFactoryCorpMappingUpsertRequest {

    @NotNull
    private Long schoolId;

    private String schoolNameSnapshot;

    @NotNull
    private Long corpId;

    private Boolean enabled;

    private String remark;
}
