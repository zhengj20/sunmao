<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-tag type="warning" effect="light">待办任务 {{ total }} 条</el-tag>
          <span class="tip">到达超过 48 小时的任务将被超时提醒/升级</span>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="bizTitle" label="单据标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ bizLabel(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单据金额(元)" width="140" align="right">
          <template #default="{ row }">
            <span class="num">{{ money(row.bizAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="当前节点" width="130" show-overflow-tooltip />
        <el-table-column label="签署方式" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.countersign === 'ALL'" size="small" type="warning" effect="plain">会签</el-tag>
            <el-tag v-else size="small" effect="plain">或签</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批人" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.assigneeName }}
            <el-tag v-if="row.agentName && row.agentName !== row.assigneeName" size="small" type="warning" effect="plain">代办</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitterName" label="提交人" width="90" />
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="到达时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.arrivedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link @click="openApprove(row)">同意</el-button>
            <el-button type="danger" link @click="openReject(row)">驳回</el-button>
            <el-button type="primary" link @click="openTransfer(row)">转交</el-button>
            <el-button type="warning" link @click="openAddSign(row)">加签</el-button>
            <el-button link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- 同意 -->
    <el-dialog v-model="approveDlg" title="审批同意" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ current?.bizTitle }}</div>
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

    <!-- 驳回 -->
    <el-dialog v-model="rejectDlg" title="审批驳回" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ current?.bizTitle }}</div>
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

    <!-- 转交 -->
    <el-dialog v-model="transferDlg" title="任务转交" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ current?.bizTitle }}</div>
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

    <!-- 加签 -->
    <el-dialog v-model="addSignDlg" title="加签" width="460px" destroy-on-close>
      <div class="dlg-biz">{{ current?.bizTitle }}</div>
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

    <InstanceDrawer v-model="detailDlg" :instance-id="detailId" :task-id="detailTaskId" @action-done="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { approveTask, rejectTask, todoPage, transferTask, addSign, userPage } from '../../../api/workflow'
import { BIZ_TYPE, fmtTime, money } from '../../../utils/format'
import InstanceDrawer from '../components/InstanceDrawer.vue'

const bizLabel = t => BIZ_TYPE[t] || t || '-'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20 })

const load = async () => {
  loading.value = true
  try {
    const data = await todoPage(query)
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

// ============ 同意 ============
const approveDlg = ref(false)
const approveForm = reactive({ comment: '' })
const openApprove = row => {
  current.value = row
  approveForm.comment = ''
  approveDlg.value = true
}
const doApprove = async () => {
  acting.value = true
  try {
    await approveTask(current.value.taskId, approveForm.comment)
    ElMessage.success('已同意')
    approveDlg.value = false
    load()
  } finally {
    acting.value = false
  }
}

// ============ 驳回 ============
const rejectDlg = ref(false)
const rejectForm = reactive({ comment: '' })
const openReject = row => {
  current.value = row
  rejectForm.comment = ''
  rejectDlg.value = true
}
const doReject = async () => {
  if (!rejectForm.comment?.trim()) return ElMessage.warning('请填写驳回原因')
  acting.value = true
  try {
    await rejectTask(current.value.taskId, rejectForm.comment)
    ElMessage.success('已驳回')
    rejectDlg.value = false
    load()
  } finally {
    acting.value = false
  }
}

// ============ 转交 ============
const transferDlg = ref(false)
const transferForm = reactive({ toUserId: null, comment: '' })
const users = ref([])
const openTransfer = async row => {
  current.value = row
  transferForm.toUserId = null
  transferForm.comment = ''
  transferDlg.value = true
  if (!users.value.length) {
    const data = await userPage({ current: 1, size: 200, status: 1 })
    users.value = data.records || []
  }
}
const doTransfer = async () => {
  if (!transferForm.toUserId) return ElMessage.warning('请选择转交目标人')
  const u = users.value.find(u => u.id === transferForm.toUserId)
  acting.value = true
  try {
    await transferTask(current.value.taskId, {
      toUserId: transferForm.toUserId,
      toUserName: u?.realName || '',
      comment: transferForm.comment
    })
    ElMessage.success('已转交')
    transferDlg.value = false
    load()
  } finally {
    acting.value = false
  }
}

// ============ 加签 ============
const addSignDlg = ref(false)
const addSignForm = reactive({ userIds: [], before: true })
const openAddSign = async row => {
  current.value = row
  addSignForm.userIds = []
  addSignForm.before = true
  addSignDlg.value = true
  if (!users.value.length) {
    const data = await userPage({ current: 1, size: 200, status: 1 })
    users.value = data.records || []
  }
}
const doAddSign = async () => {
  if (!addSignForm.userIds.length) return ElMessage.warning('请选择加签人')
  acting.value = true
  try {
    await addSign(current.value.taskId, addSignForm.userIds, addSignForm.before)
    ElMessage.success('加签成功')
    addSignDlg.value = false
    load()
  } finally {
    acting.value = false
  }
}

// ============ 详情 ============
const detailDlg = ref(false)
const detailId = ref(null)
const detailTaskId = ref(null)
const openDetail = row => {
  detailId.value = row.instanceId
  detailTaskId.value = row.taskId
  detailDlg.value = true
}

const current = ref(null)
const acting = ref(false)

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 12px; }
.tip { font-size: 12px; color: #9ca3af; }
.dlg-biz {
  font-size: 13.5px; font-weight: 600; color: #1f2937;
  background: #eef5fb; border-radius: 4px; padding: 8px 12px; margin-bottom: 14px;
}
</style>
