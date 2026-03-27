package com.lantu.connect.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

/**
 * 安全设置实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName(value = "t_security_setting", autoResultMap = true)
public class SecuritySetting {

    @TableId(type = IdType.INPUT)
    private String key;

    private String value;
    private String label;
    private String description;
    private String type;

    /** 库中为 JSON，可为 null、字符串数组或对象数组；不可用 List&lt;Map&gt; 强绑，否则如 ["low","high"] 会反序列化失败 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object options;

    private String category;
}
