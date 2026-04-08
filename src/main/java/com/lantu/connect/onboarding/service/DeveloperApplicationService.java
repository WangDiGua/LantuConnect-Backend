package com.lantu.connect.onboarding.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.onboarding.dto.DeveloperApplicationCreateRequest;
import com.lantu.connect.onboarding.dto.DeveloperApplicationQueryRequest;
import com.lantu.connect.onboarding.entity.DeveloperApplication;

import java.util.List;

public interface DeveloperApplicationService {

    DeveloperApplication submit(Long userId, DeveloperApplicationCreateRequest request);

    List<DeveloperApplication> myApplications(Long userId);

    PageResult<DeveloperApplication> list(DeveloperApplicationQueryRequest request);

    void approve(Long id, Long reviewerId, String reviewComment);

    void reject(Long id, Long reviewerId, String reviewComment);

    void batchApprove(List<Long> ids, Long reviewerId, String reviewComment);

    void batchReject(List<Long> ids, Long reviewerId, String reviewComment);
}
