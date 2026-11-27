-- =====================================================
-- V18 工作流引擎底座切换为 Flowable：
-- wfl_instance 增加 process_instance_id，用于业务实例 ↔ Flowable 流程实例 的映射
-- （在途台账 wfl_task 仍由应用层维护，供门户/超时/统计跨模块读取）
-- =====================================================
ALTER TABLE wfl_instance
  ADD COLUMN process_instance_id VARCHAR(64) COMMENT 'Flowable流程实例ID' AFTER current_node_id;
ALTER TABLE wfl_instance
  ADD KEY idx_inst_pi (process_instance_id);
