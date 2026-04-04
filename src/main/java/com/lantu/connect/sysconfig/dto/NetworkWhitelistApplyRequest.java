package com.lantu.connect.sysconfig.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端网络白名单下发（与前端 {@code { rules: string[] }} 对齐）
 */
@Data
public class NetworkWhitelistApplyRequest {

    private List<String> rules = new ArrayList<>();
}
