package com.lantu.connect.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JaversAuditService {

    private final Javers javers;

    public void commit(String author, AuditSnapshotEntry entry) {
        try {
            javers.commit(author, entry);
        } catch (Exception ex) {
            log.warn("Javers 审计提交失败: {}", ex.getMessage());
        }
    }
}

