package com.lantu.connect.sysconfig.dto;

import lombok.Data;

@Data
public class AnnouncementUpdateRequest {

    private String title;
    private String summary;
    private String content;
    private String type;
    private Boolean pinned;
}
