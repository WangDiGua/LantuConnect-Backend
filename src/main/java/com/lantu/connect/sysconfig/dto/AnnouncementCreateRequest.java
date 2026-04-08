package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnnouncementCreateRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String summary;

    private String content;

    private String type;

    private Boolean pinned;

    /** 是否对用户端展示；未传时默认 true */
    private Boolean enabled;
}
