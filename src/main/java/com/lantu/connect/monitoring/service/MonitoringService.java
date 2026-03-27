package com.lantu.connect.monitoring.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.monitoring.dto.KpiMetric;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;

import java.util.List;
import java.util.Map;

/**
 * 监控Monitoring服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface MonitoringService {

    List<KpiMetric> kpis();

    /**
     * 近 24 小时按小时聚合的延迟与请求量（依赖 t_call_log，MySQL DATE_FORMAT）。
     */
    List<Map<String, Object>> performance();

    PageResult<CallLog> callLogs(PageQuery query);

    PageResult<AlertRecord> alerts(PageQuery query);

    PageResult<TraceSpan> traces(PageQuery query);
}
