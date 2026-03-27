package com.lantu.connect.onboarding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_developer_application")
public class DeveloperApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    @TableField(exist = false)
    private String userName;

    private String contactEmail;

    private String contactPhone;

    private String companyName;

    private String applyReason;

    /**
     * pending / approved / rejected
     */
    private String status;

    private String reviewComment;

    private Long reviewedBy;
    @TableField(exist = false)
    private String reviewedByName;

    private LocalDateTime reviewedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
