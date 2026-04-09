package com.lantu.connect.auth.dto;

/**
 * 登录页等匿名场景展示的隐私说明与用户条款（正文由配置注入，可按部署单位调整）。
 */
public record LegalNoticesVO(
        String privacyTitle,
        String privacyBody,
        String termsTitle,
        String termsBody
) {
}
