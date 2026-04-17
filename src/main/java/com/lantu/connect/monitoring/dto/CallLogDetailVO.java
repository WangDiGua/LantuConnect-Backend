package com.lantu.connect.monitoring.dto;

import com.lantu.connect.monitoring.entity.CallLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallLogDetailVO {

    private CallLog log;
    private TraceSummaryVO trace;
    private List<AlertEvidenceVO> relatedAlerts;
    private ResourceHealthEvidenceVO resourceHealth;
}
