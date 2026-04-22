package com.lantu.connect.compat.robotfactory.dto;

import com.lantu.connect.compat.robotfactory.entity.RobotFactoryProjection;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Data
@Builder
public class RobotFactorySessionContext {

    private String sessionId;
    private Long projectionId;
    private Long resourceId;
    private long createdAtMillis;
    private long lastAccessMillis;
    private SseEmitter emitter;
    private RobotFactoryProjection projection;
    private RobotFactoryResourceContext resource;
}
