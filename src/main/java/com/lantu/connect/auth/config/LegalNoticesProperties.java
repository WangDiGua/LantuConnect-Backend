package com.lantu.connect.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code lantu.legal.*}：隐私与用户条款正文，便于各校在 yml 中覆盖而无需改代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.legal")
public class LegalNoticesProperties {

    private String privacyTitle = "隐私说明";
    private String termsTitle = "用户条款";

    /**
     * 纯文本，支持换行；前端以 preserve 方式渲染。
     */
    private String privacyBody = "";

    private String termsBody = "";
}
