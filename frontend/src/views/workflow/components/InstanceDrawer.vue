<template>
  <el-drawer
    :model-value="modelValue"
    title="流程详情"
    size="560px"
    destroy-on-close
    @update:model-value="v => emit('update:modelValue', v)"
  >
    <div v-loading="loading">
      <template v-if="inst">
        <el-descriptions :column="2" border size="small" class="blk">
          <el-descriptions-item label="单据标题" :span="2">{{ inst.bizTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">
            <el-tag size="small">{{ bizLabel(inst.bizType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="单据金额">
            <span class="num">{{ money(inst.bizAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="发起人">{{ inst.submitterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ fmtTime(inst.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ inst.currentNodeId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="流程状态">
            <el-tag size="small" :type="instStatusColor(inst.status)">{{ instStatusLabel(inst.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 业务快照 -->
        <template v-if="snapItems.length">
          <div class="sec-title">单据快照</div>
          <el-descriptions :column="2" border size="small" class="blk">
            <el-descriptions-item v-for="it in snapItems" :key="it.k" :label="it.k">
              {{ it.v }}
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <!-- 审批操作 -->
        <div class="sec-title">审批操作</div>
        <div class="action-bar">
          <el-button type="success" @click="openApprove">同意</el-button>
          <el-button type="danger" @click="openReject">驳回</el-button>
          <el-button type="primary" @click="openTransfer">转交</el-button>
          <el-button type="warning" @click="openAddSign">加签</el-button>
        </div>

        <div class="sec-title">审批记录</div>
        <el-timeline v-if="tasks.length">
          <el-timeline-item
            v-for="t in tasks"
            :key="t.id"
            :type="nodeColor(t.status)"
            :hollow="t.status === 'PENDING'"
            :timestamp="fmtTime(t.actedAt || t.arrivedAt)"
          >
            <div class="tl-head">
              <span class="tl-node">{{ t.nodeName }}</span>
              <el-tag size="small" :type="taskStatusColor(t.status)">{{ taskStatusLabel(t.status) }}</el-tag>
            </div>
            <div class="tl-line">
              审批人：{{ t.assigneeName }}
              <template v-if="t.agentName && t.agentName !== t.assigneeName">
                （{{ t.agentName }} 代审）
              </template>
              <el-tag v-if="t.countersign === 'ALL'" size="small" type="warning" effect="plain" style="margin-left:6px">会签</el-tag>
            </div>
            <div v-if="t.comment" class="tl-line tl-comment">意见：{{ t.comment }}</div>
            <div v-if="t.durationMinutes != null" class="tl-line tl-sub">处理耗时：{{ fmtDuration(t.durationMinutes) }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审批记录" :image-size="70" />
      </template>
      <el-empty v-else-if="!loading" description="未找到流程实例" :image-size="70" />
    </div>

    <!-- 同意弹窗 -->
    <el-dialog v-model="approveDlg" title="审批同意" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ inst?.bizTitle }}</div>
      <el-input
        v-model="approveForm.comment"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="审批意见（可空）"
      />
      <template #footer>
        <el-button @click="approveDlg = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doApprove">确认同意</el-button>
      </template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDlg" title="审批驳回" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ inst?.bizTitle }}</div>
      <el-input
        v-model="rejectForm.comment"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="驳回原因（必填）"
      />
      <template #footer>
        <el-button @click="rejectDlg = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 转交弹窗 -->
    <el-dialog v-model="transferDlg" title="任务转交" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ inst?.bizTitle }}</div>
      <el-form label-width="80px">
        <el-form-item label="转交给" required>
          <el-select
            v-model="transferForm.toUserId"
            filterable
            placeholder="请选择转交目标人"
            style="width: 100%"
          >
            <el-option
              v-for="u in users"
              :key="u.id"
              :value="u.id"
              :label="`${u.realName}（${u.username}）`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转交说明">
          <el-input
            v-model="transferForm.comment"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="转交说明（可空）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDlg = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doTransfer">确认转交</el-button>
      </template>
    </el-dialog>

    <!-- 加签弹窗 -->
    <el-dialog v-model="addSignDlg" title="加签" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ inst?.bizTitle }}</div>
      <el-form label-width="80px">
        <el-form-item label="加签类型">
          <el-radio-group v-model="addSignForm.before">
            <el-radio :value="true">前加签（同节点会签）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="加签人" required>
          <el-select
            v-model="addSignForm.userIds"
            multiple
            filterable
            placeholder="请选择加签人"
            style="width: 100%"
          >
            <el-option
              v-for="u in users"
              :key="u.id"
              :value="u.id"
              :label="`${u.realName}（${u.username}）`"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addSignDlg = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doAddSign">确认加签</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { instanceDetail, approveTask, rejectTask, transferTask, addSign, userPage } from '../../../api/workflow'
import { BIZ_TYPE, fmtTime, money } from '../../../utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  instanceId: { type: [Number, String], default: null },
  taskId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'action-done'])

const loading = ref(false)
const inst = ref(null)
const tasks = ref([])

const snapItems = computed(() => {
  if (!inst.value?.snapshot) return []
  try {
    const o = JSON.parse(inst.value.snapshot)
    return Object.entries(o).map(([k, v]) => ({
      k: String(k),
      v: typeof v === 'object' ? JSON.stringify(v) : String(v ?? '-')
    }))
  } catch {
    return []
  }
})

const bizLabel = t => BIZ_TYPE[t] || t || '-'

const instStatusLabel = s =>
  ({ RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[s] || s)
const instStatusColor = s =>
  ({ RUNNING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }[s] || 'info')

const taskStatusLabel = s =>
  ({ PENDING: '待审批', APPROVED: '已同意', REJECTED: '已驳回', TRANSFERRED: '已转办', CANCELLED: '已作废' }[s] || s)
const taskStatusColor = s =>
  ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', TRANSFERRED: 'info', CANCELLED: 'info' }[s] || 'info')
const nodeColor = s => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info')

const fmtDuration = min => {
  if (min == null) return '-'
  if (min < 60) return `${min}分钟`
  if (min < 60 * 24) return `${Math.floor(min / 60)}小时${min % 60}分钟`
  return `${Math.floor(min / 1440)}天${Math.floor((min % 1440) / 60)}小时`
}

const load = async id => {
  if (!id) return
  loading.value = true
  try {
    const data = await instanceDetail(id)
    inst.value = data.instance
    tasks.value = data.tasks || []
  } finally {
    loading.value = false
  }
}

// ============ 用户列表 ============
const users = ref([])
const loadUsers = async () => {
  if (!users.value.length) {
    const data = await userPage({ current: 1, size: 200, status: 1 })
    users.value = data.records || []
  }
}

// ============ 同意 ============
const approveDlg = ref(false)
const approveForm = reactive({ comment: '' })
const acting = ref(false)
const openApprove = () => {
  approveForm.comment = ''
  approveDlg.value = true
}
const doApprove = async () => {
  acting.value = true
  try {
    await approveTask(props.taskId, approveForm.comment)
    ElMessage.success('已同意')
    approveDlg.value = false
    emit('action-done')
    load(props.instanceId)
  } finally {
    acting.value = false
  }
}

// ============ 驳回 ============
const rejectDlg = ref(false)
const rejectForm = reactive({ comment: '' })
const openReject = () => {
  rejectForm.comment = ''
  rejectDlg.value = true
}
const doReject = async () => {
  if (!rejectForm.comment?.trim()) return ElMessage.warning('请填写驳回原因')
  acting.value = true
  try {
    await rejectTask(props.taskId, rejectForm.comment)
    ElMessage.success('已驳回')
    rejectDlg.value = false
    emit('action-done')
    load(props.instanceId)
  } finally {
    acting.value = false
  }
}

// ============ 转交 ============
const transferDlg = ref(false)
const transferForm = reactive({ toUserId: null, comment: '' })
const openTransfer = async () => {
  transferForm.toUserId = null
  transferForm.comment = ''
  transferDlg.value = true
  await loadUsers()
}
const doTransfer = async () => {
  if (!transferForm.toUserId) return ElMessage.warning('请选择转交目标人')
  const u = users.value.find(u => u.id === transferForm.toUserId)
  acting.value = true
  try {
    await transferTask(props.taskId, {
      toUserId: transferForm.toUserId,
      toUserName: u?.realName || '',
      comment: transferForm.comment
    })
    ElMessage.success('已转交')
    transferDlg.value = false
    emit('action-done')
    load(props.instanceId)
  } finally {
    acting.value = false
  }
}

// ============ 加签 ============
const addSignDlg = ref(false)
const addSignForm = reactive({ userIds: [], before: true })
const openAddSign = async () => {
  addSignForm.userIds = []
  addSignForm.before = true
  addSignDlg.value = true
  await loadUsers()
}
const doAddSign = async () => {
  if (!addSignForm.userIds.length) return ElMessage.warning('请选择加签人')
  acting.value = true
  try {
    await addSign(props.taskId, addSignForm.userIds, addSignForm.before)
    ElMessage.success('加签成功')
    addSignDlg.value = false
    emit('action-done')
    load(props.instanceId)
  } finally {
    acting.value = false
  }
}

watch(
  () => [props.modelValue, props.instanceId],
  ([open, id]) => {
    if (open && id) {
      inst.value = null
      tasks.value = []
      load(id)
    }
  }
)
</script>

<style scoped>
.blk { margin-bottom: 14px; }
.sec-title {
  font-size: 13.5px; font-weight: 700; color: #0c447c;
  margin: 16px 0 12px; padding-left: 8px;
  border-left: 3px solid #185fa5;
}
.tl-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.tl-node { font-weight: 600; font-size: 13.5px; }
.tl-line { font-size: 12.5px; color: #4b5563; line-height: 1.8; }
.tl-comment { color: #1f2937; }
.tl-sub { color: #9ca3af; }
.action-bar { display: flex; gap: 10px; margin-bottom: 10px; }
.dlg-biz {
  font-size: 13.5px; font-weight: 600; color: #1f2937;
  background: #eef5fb; border-radius: 4px; padding: 8px 12px; margin-bottom: 14px;
}
</style>
