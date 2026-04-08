package com.lantu.connect.auth.dto;

import lombok.Data;

/**
 * 认证 ProfileUpdateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class ProfileUpdateRequest {

    private String avatar;
    private String language;
}
