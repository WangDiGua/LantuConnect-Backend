package com.lantu.connect.search.controller;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.support.GatewayCallerResolver;
import com.lantu.connect.search.dto.GlobalSearchResponse;
import com.lantu.connect.search.service.GlobalSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "全局搜索")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final GatewayCallerResolver gatewayCallerResolver;

    @GetMapping("/global")
    @Operation(summary = "全局搜索 / 快速跳转中心")
    public R<GlobalSearchResponse> global(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "scope", required = false, defaultValue = "all") String scope,
            @RequestParam(value = "limitPerGroup", required = false, defaultValue = "6") Integer limitPerGroup) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return R.ok(globalSearchService.search(userId, query, scope, limitPerGroup));
    }
}
