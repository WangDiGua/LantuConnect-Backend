package com.lantu.connect.compat.robotfactory.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RobotFactoryWhitelistService {

    private final RobotFactorySettingsService settingsService;
    private final ClientIpResolver clientIpResolver;

    public String requireAllowed(HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        List<String> allowedIps = settingsService.getAllowedIps();
        if (allowedIps == null || allowedIps.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "软件工厂来源 IP 白名单未配置");
        }
        boolean matched = allowedIps.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(ip -> ip.equals(clientIp));
        if (!matched) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前来源 IP 不在软件工厂白名单内");
        }
        return clientIp;
    }
}
