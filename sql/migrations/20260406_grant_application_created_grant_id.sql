-- 授权申请审批通过后关联 t_resource_invoke_grant.id，便于从待办列表直接撤销生效授权
-- 已执行过请勿重复执行（重复会报 Duplicate column）
ALTER TABLE t_resource_grant_application
  ADD COLUMN created_grant_id BIGINT NULL
    COMMENT '审批通过后写入的 t_resource_invoke_grant.id；撤销生效授权后置空'
    AFTER review_time;
