package com.lantu.connect.usersettings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "各 resourceId 是否满足「存在且已 published」的快速判断；真正 invoke 仍走网关 Key/scope 等校验")
public class InvokeEligibilityResponse {

    private Map<String, Boolean> byResourceId;
}
