package com.lantu.connect.gateway.catalog;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * 按当前出站代理设置创建 {@link RestTemplate}（避免 Bean 级单例无法随超管配置热更新）。
 */
public final class SkillCatalogOutboundRestTemplateFactory {

    private SkillCatalogOutboundRestTemplateFactory() {
    }

    public static RestTemplate create(SkillExternalCatalogProperties.OutboundHttpProxy proxy) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(45_000);
        if (proxy != null && StringUtils.hasText(proxy.getHost()) && proxy.getPort() > 0) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost().trim(), proxy.getPort())));
        }
        return new RestTemplate(factory);
    }
}
