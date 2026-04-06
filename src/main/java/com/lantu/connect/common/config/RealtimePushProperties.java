package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 实时 WebSocket 推送开关（可按域关闭以便回滚为纯 HTTP）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.realtime")
public class RealtimePushProperties {

    /** 健康检查配置状态推送（探活任务、管理端保存） */
    private boolean pushHealth = true;

    /** 资源熔断状态推送 */
    private boolean pushCircuit = true;

    /** 告警 firing 等平台事件（收件人：monitor:view） */
    private boolean pushAlert = true;

    /** 待审核队列数量变化（防抖后推送，收件人：monitor:view） */
    private boolean pushAudit = true;

    /** 监控 KPI 等指标摘要（收件人与告警一致：platform_admin / reviewer） */
    private boolean pushMonitoringKpi = true;

    /** monitor:view 用户列表查询结果缓存毫秒数，减轻探活任务频率下的 DB 压力 */
    private long monitorRecipientCacheTtlMs = 60_000L;
}
