package com.lantu.connect.monitoring.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.monitoring.dto.AlertActionNoteRequest;
import com.lantu.connect.monitoring.dto.AlertAssignRequest;
import com.lantu.connect.monitoring.dto.AlertBatchActionRequest;
import com.lantu.connect.monitoring.dto.AlertRecordActionVO;
import com.lantu.connect.monitoring.dto.AlertRecordDetailVO;
import com.lantu.connect.monitoring.dto.AlertResolveRequest;
import com.lantu.connect.monitoring.dto.AlertRuleScopeOptionVO;
import com.lantu.connect.monitoring.dto.AlertSilenceRequest;
import com.lantu.connect.monitoring.dto.AlertSummaryVO;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.entity.AlertRecord;

import java.util.List;

public interface AlertCenterService {

    PageResult<AlertRecord> pageEvents(PageQuery query, Long currentUserId);

    AlertSummaryVO summary(Long currentUserId);

    AlertRecordDetailVO detail(String id);

    List<AlertRecordActionVO> actions(String id);

    void ack(String id, Long operatorUserId, AlertActionNoteRequest request);

    void assign(String id, Long operatorUserId, AlertAssignRequest request);

    void silence(String id, Long operatorUserId, AlertSilenceRequest request);

    void resolve(String id, Long operatorUserId, AlertResolveRequest request);

    void reopen(String id, Long operatorUserId, AlertActionNoteRequest request);

    void batchAction(Long operatorUserId, AlertBatchActionRequest request);

    void evaluateEnabledRules();

    AlertRuleScopeOptionVO scopeOptions();
}
