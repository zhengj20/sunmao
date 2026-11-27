<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">审批中心
          <el-badge v-if="pending.length" :value="pending.length" :max="99" class="appr-badge" />
        </h2>
        <p class="page-sub">人为参与流程：工作流执行到「需审批」环节会在此暂停 — 审核数字员工已完成环节的产出，批准继续或驳回终止</p>
      </div>
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </div>

    <!-- 待审批 -->
    <div class="sec-title">待审批 <el-tag v-if="pending.length" size="small" type="warning">{{ pending.length }}</el-tag></div>
    <div v-if="!pending.length" class="appr-empty">
      ✓ 暂无待审批项。将工作流环节标记为「需人工审批」后，执行到该环节会在此暂停，等待你批准或驳回。
      <el-button link type="primary" @click="router.push('/digital/workflow')">去编排工作流 →</el-button>
    </div>
    <div v-for="p in pending" :key="p.approval.id" class="appr-card">
      <div class="appr-head">
        <div class="appr-title">
          <b>{{ p.approval.stepName }}</b>
          <span class="muted">· {{ p.employeeName || '数字员工' }} · {{ p.taskName || '任务' }}</span>
        </div>
        <span class="appr-tag">待审批 · {{ sinceMins(p.approval.createdAt) }} 分钟</span>
      </div>
      <div class="appr-meta muted">
        工作流：{{ p.wfName }}
        <span v-if="p.progress">　进度 {{ p.progress }}</span>
      </div>
      <div v-if="p.outputPreview" class="appr-out">
        <div class="appr-out-h">上游环节产出（供审核）</div>
        <pre>{{ p.outputPreview }}</pre>
      </div>
      <div class="appr-actions">
        <el-input v-model="p.comment" placeholder="审批意见（可选）" size="default" style="flex:1;min-width:200px" />
        <el-button type="success" :loading="p._busy" @click="onDecide(p, 'approve')">✓ 批准并继续</el-button>
        <el-button type="danger" plain :loading="p._busy" @click="onDecide(p, 'reject')">✕ 驳回</el-button>
      </div>
    </div>

    <!-- 审批历史 -->
    <div class="sec-title">审批历史</div>
    <el-card shadow="never">
      <el-table :data="history" size="small" stripe>
        <el-table-column prop="stepName" label="环节" min-width="140" />
        <el-table-column label="决策" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'danger'" size="small">
              {{ row.status === 'APPROVED' ? '已批准' : '已驳回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="审批意见" min-width="160" show-overflow-tooltip />
        <el-table-column label="决策时间" width="160">
          <template #default="{ row }">{{ fmt(row.decidedAt) }}</template>
        </el-table-column>
        <el-table-column prop="decidedBy" label="审批人" width="90" />
      </el-table>
      <el-empty v-if="!history.length" description="暂无审批记录" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { wfApprovals, wfDecide, wfApprovalHistory } from '../../../api/digital'

const router = useRouter()
const pending = ref([])
const history = ref([])
let timer = null

const fmt = s => s ? String(s).replace('T', ' ').slice(0, 16) : '—'
const sinceMins = ts => {
  if (!ts) return 0
  const t = new Date(String(ts).replace(' ', 'T')).getTime()
  return Math.max(0, Math.round((Date.now() - t) / 60000))
}

const loadAll = async () => {
  try {
    pending.value = (await wfApprovals()) || []
    history.value = (await wfApprovalHistory(20)) || []
  } catch (e) { /* ignore */ }
  schedule()
}

// 有待审批时自动轮询
const schedule = () => {
  if (timer) { clearInterval(timer); timer = null }
  if (pending.value.length) timer = setInterval(loadAll, 5000)
}

const onDecide = async (p, decision) => {
  p._busy = true
  try {
    await wfDecide(p.approval.id, decision, p.comment || '')
    ElMessage.success(decision === 'approve' ? '已批准，工作流继续执行' : '已驳回，工作流终止')
    await loadAll()
  } finally { p._busy = false }
}

onMounted(loadAll)
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 700; color: #0c447c; margin: 0; display: flex; align-items: center; gap: 8px; }
.page-sub { font-size: 13px; color: #6b7280; margin: 4px 0 0; }
.appr-badge { margin-left: 4px; }
.sec-title { font-size: 15px; font-weight: 600; color: #1f2937; margin: 18px 0 10px; display: flex; align-items: center; gap: 8px; }
.muted { color: #6b7280; }
.appr-empty {
  background: #f6faf6; border: 1px solid #d7ecd9; border-radius: 10px;
  padding: 22px 18px; font-size: 13px; color: #4c7a5a; display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.appr-card {
  background: #fff; border: 1px solid #f0e2c8; border-left: 4px solid #e6a23c; border-radius: 10px;
  padding: 14px 16px; margin-bottom: 12px;
}
.appr-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.appr-title { font-size: 14px; }
.appr-tag { font-size: 12px; color: #b76e00; background: #fdf4e3; border: 1px solid #ecd39c; border-radius: 999px; padding: 2px 10px; }
.appr-meta { font-size: 12.5px; margin-top: 6px; }
.appr-out { margin-top: 10px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; }
.appr-out-h { font-size: 12px; color: #64748b; margin-bottom: 4px; }
.appr-out pre { margin: 0; white-space: pre-wrap; word-break: break-all; font-size: 12px; color: #334155; font-family: inherit; }
.appr-actions { display: flex; gap: 8px; margin-top: 12px; align-items: center; flex-wrap: wrap; }
</style>
