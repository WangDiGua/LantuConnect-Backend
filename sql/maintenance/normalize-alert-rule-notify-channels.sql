-- 幂等：与前端「仅站内通知」一致；告警送达走站内消息，此列恒为空数组
UPDATE `t_alert_rule` SET `notify_channels` = '[]';
