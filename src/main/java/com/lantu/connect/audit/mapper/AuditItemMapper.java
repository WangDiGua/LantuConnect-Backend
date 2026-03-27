package com.lantu.connect.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.audit.entity.AuditItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核 AuditItem 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface AuditItemMapper extends BaseMapper<AuditItem> {
}
