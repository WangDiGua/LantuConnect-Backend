package com.lantu.connect.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_skill_external_catalog_item")
public class SkillExternalCatalogItem {

    @TableId(value = "dedupe_key", type = IdType.INPUT)
    private String dedupeKey;

    private String externalId;
    private String name;
    private String summary;
    @TableField("pack_url")
    private String packUrl;
    @TableField("license_note")
    private String licenseNote;
    @TableField("source_url")
    private String sourceUrl;
    private Integer stars;

    /** 同步时预取的 SKILL.md；详情优先读此字段 */
    @TableField("skill_md")
    private String skillMd;
    @TableField("skill_md_resolved_url")
    private String skillMdResolvedUrl;
    @TableField("skill_md_truncated")
    private Boolean skillMdTruncated;

    @TableField("sync_batch")
    private Long syncBatch;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
