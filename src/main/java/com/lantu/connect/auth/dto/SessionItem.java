package com.lantu.connect.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionItem {

    private String id;
    private String device;
    private String os;
    private String browser;
    private String ip;
    private String location;
    private String loginAt;
    private String lastActiveAt;
    private Boolean current;
}
