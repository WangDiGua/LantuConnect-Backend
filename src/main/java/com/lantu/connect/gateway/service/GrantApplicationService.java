package com.lantu.connect.gateway.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.dto.GrantApplicationRequest;
import com.lantu.connect.gateway.dto.GrantApplicationVO;

public interface GrantApplicationService {

    Long apply(Long applicantUserId, GrantApplicationRequest request);

    void approve(Long reviewerUserId, Long applicationId);

    void reject(Long reviewerUserId, Long applicationId, String reason);

    PageResult<GrantApplicationVO> pageMyApplications(Long applicantUserId, String status, String keyword, int page, int pageSize);

    PageResult<GrantApplicationVO> pagePendingApplications(Long operatorUserId, String status, String keyword, int page, int pageSize);
}
