package com.lantu.connect.common.sensitive;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

    private String category;

    private Integer severity;

    private String source;

    private Boolean enabled;

    private Long createdBy;
    @TableField(exist = false)
    private String createdByName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
