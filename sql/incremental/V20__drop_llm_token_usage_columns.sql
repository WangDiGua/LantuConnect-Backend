-- Remove unused LLM billing / token usage columns (not JWT or API keys).
ALTER TABLE `t_call_log`
  DROP COLUMN `model`,
  DROP COLUMN `input_tokens`,
  DROP COLUMN `output_tokens`,
  DROP COLUMN `cost`;

ALTER TABLE `t_usage_record`
  DROP COLUMN `token_cost`;
