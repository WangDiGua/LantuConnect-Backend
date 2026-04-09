-- Remove Anthropic zip skill-pack mode: delete pack skills and columns; platform skills are hosted-only.

SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_v35_pack_skill_ids (id BIGINT NOT NULL PRIMARY KEY) ENGINE = MEMORY;

INSERT INTO tmp_v35_pack_skill_ids (id)
SELECT e.resource_id
FROM t_resource_skill_ext e
WHERE LOWER(TRIM(e.execution_mode)) = 'pack';

-- Orphan relations / tags for removed skills
DELETE spd
FROM t_skill_pack_download_event spd
         INNER JOIN tmp_v35_pack_skill_ids p ON p.id = spd.resource_id;

DELETE tr
FROM t_resource_tag_rel tr
         INNER JOIN tmp_v35_pack_skill_ids p ON p.id = tr.resource_id
WHERE tr.resource_type = 'skill';

DELETE rr
FROM t_resource_relation rr
         INNER JOIN tmp_v35_pack_skill_ids p ON p.id = rr.from_resource_id;

DELETE rr
FROM t_resource_relation rr
         INNER JOIN tmp_v35_pack_skill_ids p ON p.id = rr.to_resource_id;

DELETE FROM t_audit_item WHERE target_type = 'skill' AND target_id IN (SELECT id FROM tmp_v35_pack_skill_ids);

DELETE FROM t_resource_draft WHERE resource_id IN (SELECT id FROM tmp_v35_pack_skill_ids);

DELETE rhr
FROM t_review_helpful_rel rhr
         INNER JOIN t_review rev ON rev.id = rhr.review_id
WHERE rev.target_type = 'skill'
  AND rev.target_id IN (SELECT id FROM tmp_v35_pack_skill_ids);

DELETE FROM t_review WHERE target_type = 'skill' AND target_id IN (SELECT id FROM tmp_v35_pack_skill_ids);

DELETE cb
FROM t_resource_circuit_breaker cb
         INNER JOIN tmp_v35_pack_skill_ids p ON p.id = cb.resource_id;

DELETE hc
FROM t_resource_health_config hc
         INNER JOIN tmp_v35_pack_skill_ids p ON p.id = hc.resource_id;

-- t_resource_grant_application 可能已下线（见 migrations DROP）；无表时忽略。
UPDATE t_usage_record ur
    INNER JOIN tmp_v35_pack_skill_ids p ON p.id = ur.resource_id
SET ur.resource_id = NULL;

-- Hard-delete pack skill resources (ext/version cascade from FK on t_resource where present)
DELETE r
FROM t_resource r
         INNER JOIN tmp_v35_pack_skill_ids p ON p.id = r.id;

DROP TEMPORARY TABLE tmp_v35_pack_skill_ids;

-- Any remaining skill rows should be hosted
UPDATE t_resource_skill_ext
SET execution_mode = 'hosted'
WHERE LOWER(TRIM(execution_mode)) <> 'hosted'
   OR execution_mode IS NULL;

-- Dropping pack_validation_* columns removes idx_skill_pack_validation if present.
ALTER TABLE t_resource_skill_ext
    DROP COLUMN artifact_uri,
    DROP COLUMN artifact_sha256,
    DROP COLUMN pack_validation_status,
    DROP COLUMN pack_validated_at,
    DROP COLUMN pack_validation_message,
    DROP COLUMN skill_root_path,
    MODIFY COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'hosted' COMMENT 'hosted skill only, zip pack removed',
    MODIFY COLUMN skill_type VARCHAR(16) NOT NULL COMMENT 'e.g. hosted_v1';
