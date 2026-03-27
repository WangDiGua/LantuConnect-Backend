package com.lantu.connect.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Notification 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
