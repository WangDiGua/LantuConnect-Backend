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
@Schema(description = "各 resourceId 对 invoke 是否通过 Grant/策略校验（与 ResourceInvokeGrantService 一致）")
public class InvokeEligibilityResponse {

    private Map<String, Boolean> byResourceId;
}
