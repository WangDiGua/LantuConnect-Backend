package com.lantu.connect.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.javers.common.exception.JaversException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JaversAuditService {

    private final Javers javers;

    public void commit(String author, AuditSnapshotEntry entry) {
        try {
            javers.commit(author, entry);
        } catch (JaversException ex) {
            log.warn("Javers 审计提交失败: {}", ex.getMessage());
        }
    }
}

