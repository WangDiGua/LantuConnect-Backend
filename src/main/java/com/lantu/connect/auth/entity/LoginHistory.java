package com.lantu.connect.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_login_history")
public class LoginHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private LocalDateTime loginTime;
    private String ip;
    private String userAgent;
    private String loginType;
    private String result;
    private String failureReason;
    private String location;
    private String device;
    private String os;
    private String browser;
}
