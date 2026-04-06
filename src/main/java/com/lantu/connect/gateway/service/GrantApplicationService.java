package com.lantu.connect.gateway.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.dto.GrantApplicationRequest;
import com.lantu.connect.gateway.dto.GrantApplicationVO;

public interface GrantApplicationService {

    Long apply(Long applicantUserId, GrantApplicationRequest request);

    void approve(Long reviewerUserId, Long applicationId);

    void reject(Long reviewerUserId, Long applicationId, String reason);

    /**
     * 对已通过的申请撤销其建立的资源调用授权（与资源授权管理中的撤销一致）。
     */
    void revokeEffectiveGrant(Long operatorUserId, Long applicationId);

    PageResult<GrantApplicationVO> pageMyApplications(Long applicantUserId, String status, String keyword, int page, int pageSize);

    PageResult<GrantApplicationVO> pagePendingApplications(Long operatorUserId, String status, String keyword, int page, int pageSize);
}
