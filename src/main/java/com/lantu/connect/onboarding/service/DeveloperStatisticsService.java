package com.lantu.connect.onboarding.service;

import com.lantu.connect.onboarding.dto.DeveloperStatistics;

public interface DeveloperStatisticsService {

    DeveloperStatistics getMyStatistics(Long userId);
}
