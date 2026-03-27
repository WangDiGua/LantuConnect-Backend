package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lantu.contract")
public class BackendContractProperties {

    /**
     * 后端契约冻结版本，例如 2026-03-contract-v1
     */
    private String freezeVersion = "2026-03-contract-v1";

    /**
     * 契约冻结生效日期（yyyy-MM-dd）
     */
    private String freezeEffectiveDate = "2026-03-24";
}
