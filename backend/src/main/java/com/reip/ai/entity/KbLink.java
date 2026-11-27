package com.reip.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.reip.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_link")
public class KbLink extends BaseEntity {
    private Long sourceDocId;
    private Long targetDocId;
    private String relationType;
    private String label;
}
