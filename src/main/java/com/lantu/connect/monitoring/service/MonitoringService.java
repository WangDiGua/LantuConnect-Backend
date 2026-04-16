package com.lantu.connect.monitoring.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.monitoring.dto.KpiMetric;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.dto.PerformanceAnalysisVO;
import com.lantu.connect.monitoring.dto.QualityHistoryPointVO;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

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
     *
     * @param resourceType {@code null}/空/{@code all} 表示全部类型；{@code unknown} 表示类型为空或 unknown
     */
    List<Map<String, Object>> performance(String resourceType);

    PerformanceAnalysisVO performanceAnalysis(String window, String resourceType, Long resourceId);

    /**
     * 时间窗内按 resource_type 汇总的调用次数（用于监控概览五类资源占比）。
     *
     * @param windowHours 回看小时数，默认 24，最大 168
     */
    List<Map<String, Object>> callSummaryByResource(int windowHours);

    PageResult<CallLog> callLogs(PageQuery query);

    PageResult<AlertRecord> alerts(PageQuery query);

    PageResult<TraceSpan> traces(PageQuery query);

    List<QualityHistoryPointVO> qualityHistory(String resourceType, Long resourceId, LocalDateTime from, LocalDateTime to);
}
