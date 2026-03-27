package com.lantu.connect.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sms_verify_code")
public class SmsVerifyCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;
    private String code;
    private String purpose;
    private String status;
    private String ip;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private LocalDateTime verifyTime;
}
