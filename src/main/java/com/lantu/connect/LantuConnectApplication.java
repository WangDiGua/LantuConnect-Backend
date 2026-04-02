package com.lantu.connect;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan({"com.lantu.connect.**.mapper", "com.lantu.connect.common.sensitive"})
@Slf4j
public class LantuConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(LantuConnectApplication.class, args);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> readyListener() {
        return event -> {
            Environment env = event.getApplicationContext().getEnvironment();
            String appName = env.getProperty("spring.application.name", "nexusai-connect");
            String port = env.getProperty("server.port", "8080");
            String contextPath = env.getProperty("server.servlet.context-path", "/regis");
            String host = "localhost";
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ignored) {}

            String baseUrl = "http://" + host + ":" + port + contextPath;
            String swaggerUrl = baseUrl + "/swagger-ui.html";
            String actuatorUrl = baseUrl + "/actuator/health";

            log.info("");
            log.info("============================================================");
            log.info("  [*] {} 启动成功!", appName);
            log.info("============================================================");
            log.info("  [应用名称] {}", appName);
            log.info("  [本地地址] http://localhost:{}{}", port, contextPath);
            log.info("  [网络地址] {}", baseUrl);
            log.info("  [API文档]  {}", swaggerUrl);
            log.info("  [健康检查] {}", actuatorUrl);
            log.info("============================================================");
        };
    }
}
