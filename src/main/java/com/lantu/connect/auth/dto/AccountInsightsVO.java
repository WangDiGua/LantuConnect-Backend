package com.lantu.connect.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 个人资料页：安全态势与登录活跃度聚合（基于登录历史与当前会话）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInsightsVO {

    /** 安全分 0–100，越高越好 */
    private int securityScore;

    /**
     * 等级：{@code good} | {@code fair} | {@code warn}，
     * 与 {@link #securityLabel} 中文文案对应。
     */
    private String securityLevel;

    /** 展示用中文，如：良好 / 一般 / 注意 */
    private String securityLabel;

    /** 历史累计「成功」登录次数（用于与分页 total 对齐的补充信息） */
    private long totalSuccessLogins;

    /** 本月（自然月）成功登录次数，用于活跃度主数字 */
    private long loginCountThisMonth;

    /**
     * 最近 7 天（含今天）每日成功登录次数，按时间正序：最早一天在前。
     */
    private List<Integer> recentSuccessByDay;

    /** 与 {@link #recentSuccessByDay} 一一对应的日期 {@code yyyy-MM-dd}（本地日界线） */
    private List<String> recentDayLabels;

    /** 近 30 天内失败或锁定类登录记录条数（供扩展展示，可为 0） */
    private long failedAttemptsLast30Days;

    /** 当前 Redis 中活跃会话数 */
    private long activeSessionCount;
}
