package com.lantu.connect.compat.robotfactory.controller;

import com.lantu.connect.compat.robotfactory.service.RobotFactoryCompatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/compat/robot-factory/mcp")
@RequiredArgsConstructor
public class RobotFactoryMcpCompatController {

    private final RobotFactoryCompatService compatService;

    @GetMapping(value = "/{projectionCode}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter openSse(@PathVariable String projectionCode,
                              HttpServletRequest request) throws java.io.IOException {
        return compatService.openSse(projectionCode, request);
    }

    @PostMapping(value = "/{projectionCode}/message", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> message(@PathVariable String projectionCode,
                                          @RequestParam("session_id") String sessionId,
                                          @RequestBody(required = false) String body,
                                          HttpServletRequest request) {
        return ResponseEntity.ok(compatService.handleMessage(projectionCode, sessionId, body == null ? "{}" : body, request));
    }
}
