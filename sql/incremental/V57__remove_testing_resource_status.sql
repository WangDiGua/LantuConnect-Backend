UPDATE `t_audit_item`
SET `status` = 'published',
    `review_time` = COALESCE(`review_time`, NOW())
WHERE `status` = 'testing';

UPDATE `t_resource`
SET `status` = 'published',
    `update_time` = NOW()
WHERE `status` = 'testing';

UPDATE `t_resource_version`
SET `snapshot_json` = JSON_SET(COALESCE(`snapshot_json`, JSON_OBJECT()), '$.status', 'published')
WHERE JSON_UNQUOTE(JSON_EXTRACT(`snapshot_json`, '$.status')) = 'testing';
