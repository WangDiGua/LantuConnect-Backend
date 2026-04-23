package com.lantu.connect.sysconfig.entity;

import lombok.Data;

/**
 * 安全设置实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
public class SecuritySetting {

    private String key;

    private String value;
    private String label;
    private String description;
    private String type;

    /** 库中为 JSON，可为 null、字符串数组或对象数组；不可用 List&lt;Map&gt; 强绑，否则如 ["low","high"] 会反序列化失败 */
    private Object options;

    private String category;
}
