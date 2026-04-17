package com.lantu.connect.monitoring.dto;

import com.lantu.connect.monitoring.entity.CallLog;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TraceDetailVO {

    private TraceSummaryVO summary;
    private TraceRootCauseVO rootCause;
    private List<TraceSpanVO> spans;
    private List<CallLog> callLogs;
}
