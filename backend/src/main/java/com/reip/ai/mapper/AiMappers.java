package com.reip.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reip.ai.entity.*;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 数字员工模块 Mapper 集合（风格同 MdmMappers / SaleMappers）
 */
public interface AiMappers {

    @Mapper interface ModelConfigMapper extends BaseMapper<AiModelConfig> {}

    @Mapper interface PromptMapper extends BaseMapper<AiPrompt> {}

    @Mapper interface KbDocumentMapper extends BaseMapper<AiKbDocument> {}

    @Mapper interface KbLinkMapper extends BaseMapper<KbLink> {}

    @Mapper interface MetricSemanticMapper extends BaseMapper<AiMetricSemantic> {}

    @Mapper interface ChatSessionMapper extends BaseMapper<AiChatSession> {}

    @Mapper interface ChatMessageMapper extends BaseMapper<AiChatMessage> {}

    @Mapper interface TaskMapper extends BaseMapper<AiTask> {}

    @Mapper interface AuditLogMapper extends BaseMapper<AiAuditLog> {}
}
