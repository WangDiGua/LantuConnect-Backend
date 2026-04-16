ALTER TABLE `t_resource`
    DROP COLUMN `provider_id`;

DROP TABLE IF EXISTS `t_provider`;

UPDATE `t_platform_role` r
SET r.`permissions` = (
    SELECT JSON_ARRAYAGG(j.perm)
    FROM JSON_TABLE(r.`permissions`, '$[*]' COLUMNS (perm VARCHAR(128) PATH '$')) AS j
    WHERE j.perm NOT IN ('provider:view', 'provider:manage')
)
WHERE JSON_SEARCH(r.`permissions`, 'one', 'provider:view') IS NOT NULL
   OR JSON_SEARCH(r.`permissions`, 'one', 'provider:manage') IS NOT NULL;
