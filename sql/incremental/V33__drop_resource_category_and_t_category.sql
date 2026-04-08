-- Remove business taxonomy (t_category) and resource.category_id; catalog uses t_tag + t_resource_tag_rel only.

ALTER TABLE t_resource DROP COLUMN category_id;

DROP TABLE IF EXISTS t_category;
