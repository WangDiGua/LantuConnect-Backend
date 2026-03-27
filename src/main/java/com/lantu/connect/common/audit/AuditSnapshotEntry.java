package com.lantu.connect.common.audit;

import lombok.Data;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

import java.time.LocalDateTime;

@Data
@TypeName("AuditSnapshotEntry")
public class AuditSnapshotEntry {

    @Id
    private String id;
    private String action;
    private String resource;
    private String userId;
    private String result;
    private String traceId;
    private LocalDateTime createTime;
    private Object requestArgs;
    private Object responseBody;
}

