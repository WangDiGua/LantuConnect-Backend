package com.lantu.connect.onboarding.dto;

import lombok.Data;

@Data
public class DeveloperApplicationQueryRequest {

    private Integer page = 1;

    private Integer pageSize = 20;

    private String status;

    private String keyword;
}
