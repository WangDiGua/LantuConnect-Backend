package com.lantu.connect.gateway.dto;

import com.lantu.connect.common.validation.VersionText;
import lombok.Data;

import java.util.Map;

@Data
public class ResourceVersionCreateRequest {

    @VersionText
    private String version;

    private Boolean makeCurrent;

    /**
     * 可选快照，未传时按当前资源自动生成。
     */
    private Map<String, Object> snapshot;
}

