package com.lantu.connect.monitoring.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 链路追踪实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName(value = "t_trace_span", autoResultMap = true)
public class TraceSpan {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String traceId;
    private String parentId;
    private String operationName;
    private String serviceName;
    private LocalDateTime startTime;
    private Integer duration;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tags;

    /** 库中 JSON 结构不固定，禁用 List&lt;Map&gt; 强类型以免反序列化失败 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object logs;
}
