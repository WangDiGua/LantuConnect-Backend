package com.lantu.connect.common.integration;

import lombok.Data;

/**
 * 网络下发请求
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
public class NetworkApplyRequest {

    private String agentName;

    private String networkType;

    private String cidr;

    private String description;

    private Long requesterId;

    private String requesterName;
}
