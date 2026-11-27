<template>
  <div class="designer" v-loading="loading">
    <!-- 基本信息 -->
    <el-card shadow="never" class="blk">
      <template #header>
        <div class="blk-head">
          <span class="blk-title">基本信息</span>
          <el-tag v-if="templateId" type="info" size="small">编辑模式</el-tag>
          <el-tag v-else type="success" size="small">新建模板</el-tag>
        </div>
      </template>
      <el-form label-width="92px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="模板编码">
              <el-input v-model="form.templateCode" placeholder="如 TPL_COST_CONTRACT" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="流程名称">
              <el-input v-model="form.name" placeholder="如 合同评审审批流" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="绑定单据">
              <el-select v-model="form.bizType" placeholder="选择单据类型" style="width: 100%">
                <el-option v-for="(label, code) in BIZ_TYPE" :key="code" :value="code" :label="label" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="可选，说明该流程适用场景" clearable />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 节点编排 -->
    <el-card shadow="never" class="blk">
      <template #header>
        <div class="blk-head">
          <span class="blk-title">节点编排 <span class="blk-sub">自上而下顺序执行</span></span>
          <div class="add-btns">
            <el-button size="small" type="primary" plain :icon="Plus" @click="addNode('APPROVE')">添加审批</el-button>
            <el-button size="small" type="warning" plain :icon="Plus" @click="addNode('CONDITION')">添加条件</el-button>
          </div>
        </div>
      </template>

      <div class="node-flow">
        <!-- 起点 -->
        <div class="flow-node start">
          <div class="flow-badge"><el-icon><User /></el-icon></div>
          <div class="flow-body">
            <div class="flow-name">发起</div>
            <div class="flow-desc">提交人发起流程</div>
          </div>
          <el-tag size="small" type="info" effect="plain">开始</el-tag>
        </div>
        <div class="flow-line"><el-icon class="flow-arrow"><ArrowDown /></el-icon></div>

        <template v-for="(node, idx) in nodes" :key="node.id">
          <div class="flow-node" :class="'type-' + node.type">
            <div class="flow-badge"><el-icon><component :is="nodeIcon(node.type)" /></el-icon></div>
            <div class="flow-body">
              <div class="flow-name">
                <el-tag size="small" :type="typeTag(node.type)" effect="dark">{{ typeLabel(node.type) }}</el-tag>
                <el-input
                  v-model="node.name"
                  class="node-name"
                  :placeholder="namePlaceholder(node.type)"
                />
              </div>
              <div class="node-config">
                <!-- 审批节点配置 -->
                <template v-if="node.type === 'APPROVE'">
                  <el-form label-width="88px">
                    <el-row :gutter="16">
                      <el-col :xs="24" :sm="12">
                        <el-form-item label="审批人">
                          <el-radio-group v-model="node.assigneeType" @change="onAssigneeType(node)">
                            <el-radio value="ROLE">按角色</el-radio>
                            <el-radio value="USER">指定用户</el-radio>
                          </el-radio-group>
                        </el-form-item>
                      </el-col>
                      <el-col :xs="24" :sm="12">
                        <el-form-item label="签署方式">
                          <el-radio-group v-model="node.countersign">
                            <el-radio value="ANY">或签</el-radio>
                            <el-radio value="ALL">会签</el-radio>
                          </el-radio-group>
                        </el-form-item>
                      </el-col>
                    </el-row>
                    <el-form-item label="审批人列表">
                      <el-select
                        v-if="node.assigneeType === 'ROLE'"
                        v-model="node.assignees"
                        multiple
                        filterable
                        allow-create
                        default-first-option
                        placeholder="选择或输入角色编码（如 COST_MGR）"
                        style="width: 100%"
                      >
                        <el-option v-for="r in roles" :key="r.code" :value="r.code" :label="`${r.name}（${r.code}）`" />
                      </el-select>
                      <el-select
                        v-else
                        v-model="node.assignees"
                        multiple
                        filterable
                        placeholder="选择审批用户"
                        style="width: 100%"
                      >
                        <el-option v-for="u in users" :key="u.id" :value="String(u.id)" :label="`${u.realName}（${u.username}）`" />
                      </el-select>
                    </el-form-item>
                  </el-form>

                  <!-- 字段级权限 -->
                  <div class="perm-head">
                    <span class="sub-title">字段级权限</span>
                    <el-button size="small" text type="primary" :icon="Plus" @click="addPerm(node)">添加</el-button>
                  </div>
                  <div v-if="node.fieldPerm?.length" class="perm-table">
                    <div class="perm-row perm-row-head">
                      <span class="c-field">字段</span><span class="c-vis">可见</span><span class="c-edt">可编辑</span><span class="c-op"></span>
                    </div>
                    <div v-for="(p, pi) in node.fieldPerm" :key="pi" class="perm-row">
                      <el-input v-model="p.field" size="small" placeholder="如 amount / remark" class="c-field" />
                      <el-switch v-model="p.visible" size="small" class="c-vis" />
                      <el-switch v-model="p.editable" size="small" class="c-edt" />
                      <el-button size="small" type="danger" link :icon="Delete" class="c-op" @click="removePerm(node, pi)" />
                    </div>
                  </div>
                  <div v-else class="perm-empty">未配置字段权限时，单据字段默认对审批人可见</div>
                </template>

                <!-- 条件节点配置 -->
                <template v-else-if="node.type === 'CONDITION'">
                  <div class="cond-tip">按单据金额路由：命中条件的走对应节点，未命中的走「默认」节点</div>
                  <div class="branch-list">
                    <div v-for="(b, bi) in node.branches" :key="bi" class="branch-row">
                      <el-tag size="small" :type="b.when ? 'warning' : 'info'" effect="plain">
                        {{ b.when ? '条件' : '默认' }}
                      </el-tag>
                      <template v-if="b.when">
                        <el-select v-model="b.when.op" size="small" style="width: 66px">
                          <el-option v-for="op in OPS" :key="op" :value="op" :label="OP_LABEL[op] || op" />
                        </el-select>
                        <el-input-number
                          v-model="b.when.value"
                          size="small"
                          :min="0"
                          :precision="0"
                          :step="100000"
                          controls-position="right"
                          style="width: 150px"
                        />
                        <span class="branch-unit">元</span>
                      </template>
                      <span class="branch-arrow">→</span>
                      <el-select v-model="b.next" size="small" placeholder="目标节点" style="width: 160px">
                        <el-option v-for="n in otherNodes(node)" :key="n.id" :value="n.id" :label="n.name || n.id" />
                      </el-select>
                      <el-button
                        v-if="b.when"
                        size="small"
                        type="danger"
                        link
                        :icon="Delete"
                        :disabled="node.branches.length <= 1"
                        @click="removeBranch(node, bi)"
                      />
                    </div>
                  </div>
                  <el-button size="small" text type="primary" :icon="Plus" @click="addBranch(node)">添加条件分支</el-button>
                </template>

                <!-- 结束节点 -->
                <template v-else>
                  <span class="end-text">审批通过后流程结束并归档</span>
                </template>
              </div>

              <div class="node-ops">
                <el-button size="small" :icon="Top" title="上移" :disabled="idx === 0" @click="moveNode(idx, -1)" />
                <el-button size="small" :icon="Bottom" title="下移" :disabled="idx === nodes.length - 1" @click="moveNode(idx, 1)" />
                <el-button size="small" type="danger" :icon="Delete" title="删除" :disabled="nodes.length <= 2" @click="removeNode(idx)" />
              </div>
            </div>
          </div>
          <div class="flow-line"><el-icon class="flow-arrow"><ArrowDown /></el-icon></div>
        </template>
      </div>
    </el-card>

    <!-- 底部操作 -->
    <div class="designer-foot">
      <div class="foot-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>同一单据类型仅保留一个启用模板；在途流程仍按旧版本执行。保存后新发起流程即生效。</span>
      </div>
      <div class="foot-btns">
        <el-button @click="emit('cancel')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">校验并保存</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Delete, Top, Bottom, User, ArrowDown, InfoFilled,
  Stamp, Connection, CircleCheck
} from '@element-plus/icons-vue'
import { templateDetail, saveTemplate, userPage } from '../../../api/workflow'
import { roleList } from '../../../api/sys'
import { BIZ_TYPE } from '../../../utils/format'

const props = defineProps({
  templateId: { type: [Number, String], default: null }
})
const emit = defineEmits(['saved', 'cancel'])

const loading = ref(false)
const saving = ref(false)
const roles = ref([])
const users = ref([])

const form = reactive({
  templateCode: '',
  name: '',
  bizType: '',
  remark: ''
})
const nodes = ref([])

const OPS = ['>=', '>', '<=', '<', '==']
const OP_LABEL = { '>=': '≥', '>': '>', '<=': '≤', '<': '<', '==': '=' }

let idSeq = 100
const nextId = () => 'n' + (idSeq++)

// ============ 节点工厂/归一化 ============
const newApprove = (id, name) => ({
  id, type: 'APPROVE', name,
  assigneeType: 'ROLE', assignees: [], countersign: 'ANY', fieldPerm: []
})
const newCondition = (id, name, defaultNext) => ({
  id, type: 'CONDITION', name,
  branches: [
    { when: { field: 'amount', op: '>=', value: 1000000 }, next: '' },
    { next: defaultNext || '' }
  ]
})
const newEnd = (id, name) => ({ id, type: 'END', name })

const normalize = n => {
  const type = n.type || 'APPROVE'
  const base = { id: n.id, type, name: n.name || n.id }
  if (type === 'APPROVE') {
    return {
      ...base,
      assigneeType: n.assigneeType || 'ROLE',
      assignees: Array.isArray(n.assignees) ? n.assignees : [],
      countersign: n.countersign || 'ANY',
      fieldPerm: (n.fieldPerm || []).map(p => ({ field: p.field || '', visible: p.visible !== false, editable: !!p.editable }))
    }
  }
  if (type === 'CONDITION') {
    return {
      ...base,
      branches: (n.branches || []).map(b => ({
        when: b.when ? { field: 'amount', op: b.when.op || '>=', value: Number(b.when.value) || 0 } : null,
        next: b.next || ''
      }))
    }
  }
  return base
}

// ============ 增删排序 ============
const addNode = type => {
  const id = nextId()
  const insertIdx = nodes.value[nodes.value.length - 1]?.type === 'END' ? nodes.value.length - 1 : nodes.value.length
  if (type === 'APPROVE') {
    nodes.value.splice(insertIdx, 0, newApprove(id, `审批节点${nodes.value.length + 1}`))
  } else {
    const defaultNext = nodes.value[insertIdx]?.id || ''
    nodes.value.splice(insertIdx, 0, newCondition(id, '金额路由', defaultNext))
  }
}

const removeNode = idx => {
  const removed = nodes.value[idx]
  // 清理其它条件分支对已删节点的指向
  nodes.value.forEach(n => {
    if (n.branches) n.branches.forEach(b => { if (b.next === removed.id) b.next = '' })
  })
  nodes.value.splice(idx, 1)
}

const moveNode = (idx, dir) => {
  const target = idx + dir
  if (target < 0 || target >= nodes.value.length) return
  const arr = nodes.value
  const tmp = arr[idx]
  arr[idx] = arr[target]
  arr[target] = tmp
}

const otherNodes = self => nodes.value.filter(n => n.id !== self.id)

const onAssigneeType = node => { node.assignees = [] }
const addPerm = node => { (node.fieldPerm ||= []).push({ field: '', visible: true, editable: false }) }
const removePerm = (node, i) => node.fieldPerm.splice(i, 1)
const addBranch = node => {
  node.branches.push({ when: { field: 'amount', op: '<', value: 1000000 }, next: '' })
}
const removeBranch = (node, i) => node.branches.splice(i, 1)

// ============ 展示辅助 ============
const typeLabel = t => ({ APPROVE: '审批', CONDITION: '条件', END: '结束' }[t] || t)
const typeTag = t => ({ APPROVE: 'primary', CONDITION: 'warning', END: 'success' }[t] || 'info')
const nodeIcon = t => ({ APPROVE: 'Stamp', CONDITION: 'Connection', END: 'CircleCheck' }[t] || 'Stamp')
const namePlaceholder = t =>
  t === 'APPROVE' ? '如：区域成本经理' : t === 'CONDITION' ? '如：金额路由' : '如：结束'

// ============ 加载 ============
const load = async () => {
  if (!props.templateId) {
    nodes.value = [newApprove('n1', '审批节点1'), newEnd('n2', '结束')]
    return
  }
  loading.value = true
  try {
    const t = await templateDetail(props.templateId)
    form.templateCode = t.templateCode || ''
    form.name = t.name || ''
    form.bizType = t.bizType || ''
    form.remark = t.remark || ''
    let def = null
    try { def = JSON.parse(t.definition) } catch { def = null }
    if (def?.nodes?.length) {
      nodes.value = def.nodes.map(normalize)
    } else {
      nodes.value = [newApprove('n1', '审批节点1'), newEnd('n2', '结束')]
    }
  } catch (e) {
    nodes.value = [newApprove('n1', '审批节点1'), newEnd('n2', '结束')]
  } finally {
    loading.value = false
  }
}

const loadRoles = async () => {
  try {
    const list = await roleList()
    roles.value = (list || []).filter(r => r.status === 1 || r.status === undefined || r.status === null)
  } catch { roles.value = [] }
}
const loadUsers = async () => {
  try {
    const data = await userPage({ current: 1, size: 200, status: 1 })
    users.value = data.records || []
  } catch { users.value = [] }
}

// ============ 保存 ============
const buildDefinition = () => {
  const idMap = {}
  const out = nodes.value.map((n, i) => {
    const newId = 'n' + (i + 1)
    idMap[n.id] = newId
    if (n.type === 'APPROVE') {
      return {
        id: newId, type: 'APPROVE', name: n.name,
        assigneeType: n.assigneeType, assignees: n.assignees, countersign: n.countersign,
        fieldPerm: (n.fieldPerm || []).filter(p => p.field && p.field.trim()).map(p => ({ field: p.field.trim(), visible: !!p.visible, editable: !!p.editable }))
      }
    }
    if (n.type === 'CONDITION') {
      return {
        id: newId, type: 'CONDITION', name: n.name,
        branches: n.branches.map(b => ({
          ...(b.when ? { when: { field: 'amount', op: b.when.op, value: Number(b.when.value) } } : {}),
          next: idMap[b.next] || b.next
        }))
      }
    }
    return { id: newId, type: 'END', name: n.name }
  })
  return JSON.stringify({ nodes: out, start: 'n1' })
}

const validate = () => {
  if (!form.templateCode?.trim()) return '请填写模板编码'
  if (!form.name?.trim()) return '请填写流程名称'
  if (!form.bizType) return '请选择绑定单据类型'
  if (!nodes.value.length) return '请至少添加一个节点'
  for (const n of nodes.value) {
    if (!n.name?.trim()) return `存在未命名节点，请补全节点名称`
    if (n.type === 'APPROVE' && !n.assignees?.length) return `审批节点「${n.name}」未配置审批人`
    if (n.type === 'CONDITION') {
      if (!n.branches?.length) return `条件节点「${n.name}」未配置分支`
      if (!n.branches.every(b => b.next)) return `条件节点「${n.name}」存在未选择目标节点的分支`
    }
  }
  return null
}

const save = async () => {
  const err = validate()
  if (err) return ElMessage.warning(err)

  const definition = buildDefinition()
  // 本地再校验 JSON 合法性
  try { JSON.parse(definition) } catch { return ElMessage.error('流程定义生成失败') }

  const payload = {
    id: props.templateId ? Number(props.templateId) : undefined,
    templateCode: form.templateCode.trim(),
    name: form.name.trim(),
    bizType: form.bizType,
    remark: form.remark?.trim() || null,
    definition
  }

  saving.value = true
  try {
    await saveTemplate(payload)
    ElMessage.success('模板已保存')
    emit('saved')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadRoles()
  loadUsers()
})
</script>

<style scoped>
.designer { display: flex; flex-direction: column; gap: 14px; padding: 4px 2px; }
.blk-head { display: flex; justify-content: space-between; align-items: center; }
.blk-title { font-size: 14px; font-weight: 700; color: #0c447c; }
.blk-sub { font-size: 12px; color: #9ca3af; font-weight: 400; margin-left: 8px; }
.add-btns { display: flex; gap: 8px; }

.node-flow { display: flex; flex-direction: column; align-items: flex-start; }
.flow-node {
  display: flex; align-items: flex-start; gap: 12px;
  width: 100%; border: 1px solid #e5e7eb; border-radius: 10px;
  padding: 12px 14px; background: #fff;
}
.flow-node.start { border-style: dashed; background: #f5f9fd; }
.flow-node.type-APPROVE { border-left: 3px solid #2a74bd; }
.flow-node.type-CONDITION { border-left: 3px solid #e6a23c; }
.flow-node.type-END { border-left: 3px solid #67c23a; }
.flow-badge {
  flex: none; width: 34px; height: 34px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 16px;
}
.flow-node.start .flow-badge { background: #185fa5; }
.flow-node.type-APPROVE .flow-badge { background: #2a74bd; }
.flow-node.type-CONDITION .flow-badge { background: #e6a23c; }
.flow-node.type-END .flow-badge { background: #67c23a; }
.flow-body { flex: 1; min-width: 0; }
.flow-name { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.node-name { flex: 1; }
.node-config { background: #fafbfc; border-radius: 8px; padding: 4px 12px 10px; }
.flow-desc { font-size: 12.5px; color: #9ca3af; }
.end-text { font-size: 13px; color: #6b7280; padding: 6px 0; }

.flow-line { display: flex; justify-content: flex-start; padding-left: 20px; height: 26px; }
.flow-arrow { color: #c2c8d0; font-size: 16px; }

.node-ops { flex: none; display: flex; flex-direction: column; gap: 4px; }

.perm-head { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.sub-title { font-size: 13px; font-weight: 600; color: #374151; }
.perm-table { margin-top: 8px; }
.perm-row { display: flex; align-items: center; gap: 14px; padding: 4px 0; }
.perm-row-head { font-size: 12px; color: #9ca3af; }
.c-field { flex: 1; }
.c-vis { width: 70px; }
.c-edt { width: 70px; }
.c-op { width: 30px; }
.perm-empty { font-size: 12px; color: #b0b6bf; margin-top: 6px; }

.cond-tip { font-size: 12.5px; color: #b8862b; background: #fdf6e9; border-radius: 6px; padding: 6px 10px; margin-bottom: 8px; }
.branch-list { display: flex; flex-direction: column; gap: 8px; }
.branch-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.branch-arrow { color: #9ca3af; }
.branch-unit { color: #9ca3af; font-size: 12px; }

.designer-foot {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  background: #f5f9fd; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px 14px;
}
.foot-tip { display: flex; align-items: center; gap: 6px; font-size: 12.5px; color: #4b5563; }
.foot-tip .el-icon { color: #185fa5; }
.foot-btns { display: flex; gap: 10px; flex: none; }
</style>
