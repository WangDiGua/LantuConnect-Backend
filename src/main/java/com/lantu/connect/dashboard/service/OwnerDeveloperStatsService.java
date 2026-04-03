package com.lantu.connect.dashboard.service;

import com.lantu.connect.dashboard.dto.OwnerDeveloperStatsVO;

public interface OwnerDeveloperStatsService {

    /**
     * @param operatorUserId 当前登录用户
     * @param ownerUserId    可选；缺省表示统计本人。部门管理员仅可查看同 {@code menu_id} 的 owner。
     * @param periodDays     统计近 N 天，默认 7，最大 365
     */
    OwnerDeveloperStatsVO ownerResourceStats(Long operatorUserId, Long ownerUserId, int periodDays);
}
