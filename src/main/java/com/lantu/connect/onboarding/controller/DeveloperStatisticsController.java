package com.lantu.connect.onboarding.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.onboarding.dto.DeveloperStatistics;
import com.lantu.connect.onboarding.service.DeveloperStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/developer")
@RequiredArgsConstructor
public class DeveloperStatisticsController {

    private final DeveloperStatisticsService developerStatisticsService;

    @GetMapping("/my-statistics")
    public R<DeveloperStatistics> myStatistics(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(developerStatisticsService.getMyStatistics(userId));
    }
}
