-- 资源与运营演示数据（可重复执行）
-- 用途：近期调用日志（24h/今日 KPI）、五类 resource_type 分布、链路追踪树。
-- 执行：mysql -h 127.0.0.1 -u root -proot lantu_connect < sql/seed_resource_ops_demo.sql

SET NAMES utf8mb4;

DELETE FROM t_trace_span WHERE trace_id IN ('trace-seed-ops-001', 'trace-seed-ops-002', 'trace-seed-ops-003');
DELETE FROM t_call_log WHERE id LIKE 'seed-cl-%';

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-01', 'trace-seed-ops-001', 'demo-agent-1', 'gateway-demo', 'agent', '4', 'POST /v1/invoke', 'success', 200, 420, NULL, '10.0.1.1', DATE_SUB(NOW(), INTERVAL 55 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-02', 'trace-seed-ops-001', 'demo-agent-1', 'gateway-demo', 'skill', '4', 'POST /v1/invoke', 'success', 200, 180, NULL, '10.0.1.1', DATE_SUB(NOW(), INTERVAL 50 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-03', 'trace-seed-ops-002', 'demo-agent-2', 'mcp-demo', 'mcp', '3', 'POST /v1/invoke', 'success', 200, 890, NULL, '10.0.1.2', DATE_SUB(NOW(), INTERVAL 40 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-04', 'trace-seed-ops-002', 'demo-agent-2', 'mcp-demo', 'app', '3', 'POST /v1/invoke', 'success', 200, 320, NULL, '10.0.1.2', DATE_SUB(NOW(), INTERVAL 35 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-05', 'trace-seed-ops-003', 'demo-ds-1', 'dataset-demo', 'dataset', '2', 'POST /v1/invoke', 'success', 200, 1250, NULL, '10.0.1.3', DATE_SUB(NOW(), INTERVAL 25 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-06', 'trace-seed-ops-003', 'demo-legacy', 'legacy-no-type', NULL, '2', 'POST /chat', 'success', 200, 760, NULL, '10.0.1.3', DATE_SUB(NOW(), INTERVAL 20 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-07', 'trace-seed-ops-001', 'demo-agent-1', 'gateway-demo', 'agent', '4', 'POST /v1/invoke', 'error', 500, 2100, 'upstream timeout', '10.0.1.4', DATE_SUB(NOW(), INTERVAL 12 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-08', 'trace-seed-ops-002', 'demo-agent-2', 'mcp-demo', 'skill', '3', 'POST /v1/invoke', 'success', 200, 640, NULL, '10.0.1.2', DATE_SUB(NOW(), INTERVAL 8 MINUTE));

INSERT INTO t_call_log (id, trace_id, agent_id, agent_name, resource_type, user_id, method, status, status_code, latency_ms, error_message, ip, create_time) VALUES
('seed-cl-09', 'trace-seed-ops-003', 'demo-ds-1', 'dataset-demo', 'dataset', '2', 'POST /v1/invoke', 'timeout', 504, 30000, 'deadline exceeded', '10.0.1.3', DATE_SUB(NOW(), INTERVAL 3 MINUTE));

INSERT INTO t_trace_span (id, trace_id, parent_id, operation_name, service_name, start_time, duration, status, tags, logs) VALUES
('seed-ts-ops-a1', 'trace-seed-ops-001', NULL, 'gateway.invoke', 'lantu-gateway', DATE_SUB(NOW(), INTERVAL 55 MINUTE), 120, 'ok', JSON_OBJECT('resource_type', 'agent', 'route', '/v1/invoke'), NULL);

INSERT INTO t_trace_span (id, trace_id, parent_id, operation_name, service_name, start_time, duration, status, tags, logs) VALUES
('seed-ts-ops-a2', 'trace-seed-ops-001', 'seed-ts-ops-a1', 'skill.execute', 'skill-runtime', DATE_ADD(DATE_SUB(NOW(), INTERVAL 55 MINUTE), INTERVAL 2 SECOND), 95, 'ok', JSON_OBJECT('resource_type', 'skill', 'skill_id', '101'), JSON_ARRAY(JSON_OBJECT('message', 'bind args ok', 'timestamp', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s'))));

INSERT INTO t_trace_span (id, trace_id, parent_id, operation_name, service_name, start_time, duration, status, tags, logs) VALUES
('seed-ts-ops-b1', 'trace-seed-ops-002', NULL, 'mcp.session', 'mcp-host', DATE_SUB(NOW(), INTERVAL 40 MINUTE), 200, 'ok', JSON_OBJECT('resource_type', 'mcp'), NULL);

INSERT INTO t_trace_span (id, trace_id, parent_id, operation_name, service_name, start_time, duration, status, tags, logs) VALUES
('seed-ts-ops-b2', 'trace-seed-ops-002', 'seed-ts-ops-b1', 'tool.call', 'filesystem-mcp', DATE_ADD(DATE_SUB(NOW(), INTERVAL 40 MINUTE), INTERVAL 3 SECOND), 680, 'ok', JSON_OBJECT('resource_type', 'mcp', 'tool', 'read_file'), NULL);

INSERT INTO t_trace_span (id, trace_id, parent_id, operation_name, service_name, start_time, duration, status, tags, logs) VALUES
('seed-ts-ops-c1', 'trace-seed-ops-003', NULL, 'app.orchestrate', 'workflow-engine', DATE_SUB(NOW(), INTERVAL 25 MINUTE), 300, 'ok', JSON_OBJECT('resource_type', 'app'), NULL);

INSERT INTO t_trace_span (id, trace_id, parent_id, operation_name, service_name, start_time, duration, status, tags, logs) VALUES
('seed-ts-ops-c2', 'trace-seed-ops-003', 'seed-ts-ops-c1', 'dataset.query', 'vector-store', DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 MINUTE), INTERVAL 5 SECOND), 920, 'ok', JSON_OBJECT('resource_type', 'dataset', 'top_k', '8'), NULL);

