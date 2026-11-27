<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="form.bizType" placeholder="单据类型" style="width: 160px">
            <el-option v-for="t in BIZ_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-input v-model="form.bizId" placeholder="单据ID（bizId）" clearable style="width: 200px" @keyup.enter="onPreview" />
          <el-button type="primary" :loading="loading" @click="onPreview">AI 预审</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <div class="mode-box" v-if="result">
          <el-tag :type="aiModeMeta(result.aiMode)[1]" effect="light" size="small">
            {{ aiModeMeta(result.aiMode)[0] }}
          </el-tag>
          <span class="mode-hint">当前为{{ result.aiMode === 'LLM' ? '大模型解读' : '规则引擎，配置模型后升级为 AI' }}</span>
        </div>
      </div>
    </el-card>

    <el-alert type="warning" :closable="false" show-icon class="rule-alert">
      <template #title>护栏：AI 预审意见仅供审批人参考，最终审批由人工 + 流程引擎决定，不代批。</template>
    </el-alert>

    <el-empty v-if="!result" description="选择单据类型与单据ID后，点击「AI 预审」" :image-size="100" />

    <template v-else>
      <!-- LLM 自然语言解读（可选） -->
      <el-card v-if="result.llmNote" shadow="never" class="result-card">
        <template #header><span class="card-title">AI 解读（LLM）</span></template>
        <div class="llm-note">{{ result.llmNote }}</div>
      </el-card>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-card shadow="never" class="result-card">
            <template #header><span class="card-title">单据摘要</span></template>
            <ul class="summary-list">
              <li v-for="(s, i) in result.summary" :key="i">{{ s }}</li>
              <li v-if="!result.summary || !result.summary.length" class="muted">无</li>
            </ul>
          </el-card>
        </el-col>
        <el-col :span="16">
          <el-card shadow="never" class="result-card">
            <template #header><span class="card-title">风险提示（{{ result.risks?.length || 0 }}）</span></template>
            <el-empty v-if="!result.risks || !result.risks.length" description="规则引擎未检测到风险" :image-size="60" />
            <div v-for="(r, i) in result.risks" :key="i" class="risk-item">
              <div class="risk-head">
                <el-tag :type="statusMeta(r.level, RISK_LEVEL)[1]" size="small">{{ statusMeta(r.level, RISK_LEVEL)[0] }}</el-tag>
                <span class="risk-desc">{{ r.desc }}</span>
              </div>
              <div class="risk-evidence">依据：{{ r.evidence || '-' }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 相似单据（仅销售合同） -->
      <el-card v-if="result.similar && result.similar.length" shadow="never" class="result-card">
        <template #header><span class="card-title">相似单据（同项目已审批合同）</span></template>
        <el-table :data="result.similar" border stripe size="small">
          <el-table-column prop="contractNo" label="合同号" width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.contractNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerName || '-' }}</template>
          </el-table-column>
          <el-table-column label="成交总价（元）" width="140" align="right">
            <template #default="{ row }"><span class="num">{{ money(row.contractPrice) }}</span></template>
          </el-table-column>
          <el-table-column label="折扣率" width="100" align="center">
            <template #default="{ row }">{{ row.discountRate ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="signedAt" label="签约时间" width="140" align="center">
            <template #default="{ row }">{{ fmtTime(row.signedAt) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">{{ row.status || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 相关制度文档（知识库） -->
      <el-card v-if="result.kbRefs && result.kbRefs.length" shadow="never" class="result-card">
        <template #header><span class="card-title">相关制度文档（知识库）</span></template>
        <div v-for="(doc, i) in result.kbRefs" :key="i" class="kb-ref-item">
          <div class="kb-ref-head">
            <el-tag size="small" effect="plain">{{ doc.category || '未分类' }}</el-tag>
            <span class="kb-ref-title">{{ doc.title }}</span>
          </div>
          <div class="kb-ref-content">{{ doc.content }}</div>
        </div>
      </el-card>

      <!-- advisory 提示 -->
      <el-alert v-if="result.advisory" type="info" :closable="false" show-icon class="advisory">
        <template #title>{{ result.advisory }}</template>
      </el-alert>
    </template>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { money, fmtTime } from '../../../utils/format'
import {
  approvalPreview, BIZ_TYPES, RISK_LEVEL, aiModeMeta, statusMeta
} from '../../../api/ai'

const form = reactive({ bizType: 'SALE_CONTRACT', bizId: '' })
const loading = ref(false)
const result = ref(null)

const onPreview = async () => {
  if (!form.bizType) return ElMessage.warning('请选择单据类型')
  if (!form.bizId) return ElMessage.warning('请输入单据ID（bizId）')
  loading.value = true
  result.value = null
  try {
    result.value = await approvalPreview(form.bizType, Number(form.bizId))
  } catch (e) {
    result.value = null
  } finally {
    loading.value = false
  }
}
const onReset = () => {
  form.bizType = 'SALE_CONTRACT'
  form.bizId = ''
  result.value = null
}
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.mode-box { display: flex; align-items: center; gap: 8px; }
.mode-hint { font-size: 12px; color: #909399; }
.rule-alert { margin-bottom: 12px; }
.result-card { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.summary-list { margin: 0; padding-left: 18px; }
.summary-list li { margin-bottom: 6px; line-height: 1.6; }
.muted { color: #909399; }
.llm-note { line-height: 1.7; color: #1f2937; white-space: pre-wrap; }
.risk-item { padding: 8px 0; border-bottom: 1px dashed #e5e9f0; }
.risk-item:last-child { border-bottom: none; }
.risk-head { display: flex; align-items: center; gap: 8px; }
.risk-desc { font-weight: 600; color: #1f2937; }
.risk-evidence { margin-top: 4px; margin-left: 2px; font-size: 12px; color: #909399; }
.advisory { margin-top: 4px; }
.kb-ref-item { padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.kb-ref-item:last-child { border-bottom: none; }
.kb-ref-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.kb-ref-title { font-weight: 600; color: #1f2937; }
.kb-ref-content { color: #606266; font-size: 13px; line-height: 1.6; }
</style>
