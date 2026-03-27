package com.lantu.connect.usersettings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户设置 UserStatsVO 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsVO {

    private Long totalAgents;

    private Long totalWorkflows;

    private Long totalApiCalls;

    private Long tokenUsage;

    private Long storageUsedMb;

    private Long activeSessions;

    private String period;
}
