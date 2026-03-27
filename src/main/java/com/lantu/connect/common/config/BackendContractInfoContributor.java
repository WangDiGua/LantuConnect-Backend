package com.lantu.connect.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * 将后端契约冻结信息暴露到 actuator/info，便于环境核对。
 */
@Component
@RequiredArgsConstructor
public class BackendContractInfoContributor implements InfoContributor {

    private final BackendContractProperties backendContractProperties;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("backendContract", java.util.Map.of(
                "freezeVersion", backendContractProperties.getFreezeVersion(),
                "freezeEffectiveDate", backendContractProperties.getFreezeEffectiveDate()
        ));
    }
}
