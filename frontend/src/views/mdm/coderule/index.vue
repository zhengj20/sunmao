<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="规则编码/名称" clearable style="width: 220px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增规则</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border style="width: 100%">
        <el-table-column prop="ruleCode" label="规则编码" width="100" show-overflow-tooltip />
        <el-table-column prop="name" label="规则名称" width="140" show-overflow-tooltip />
        <el-table-column prop="pattern" label="编码模式" min-width="260" show-overflow-tooltip />
        <el-table-column prop="example" label="示例编码" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.example || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="onPreview(row)">预览</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑编码规则' : '新增编码规则'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="100px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="dlg.form.ruleCode" :disabled="!!dlg.form.id" placeholder="如：HT（保存后不可修改）" maxlength="32" />
        </el-form-item>
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="dlg.form.name" placeholder="如：合同编号" maxlength="128" />
        </el-form-item>
        <el-form-item label="编码模式" prop="pattern">
          <el-input v-model="dlg.form.pattern" placeholder="如：HT-{projectCode}-{YYYY}-{seq:4}" maxlength="128" />
          <div class="form-tip">
            占位符：{YYYY}年 {YY}两位年 {MM}月 {DD}日 {seq:n}流水号 {projectCode}项目编号（上下文占位符）
          </div>
        </el-form-item>
        <el-form-item label="示例编码" prop="example">
          <el-input v-model="dlg.form.example" placeholder="如：HT-PJ2026001-2026-0001" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dlg.form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="preview.visible" title="编码预览" width="520px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="规则编码">{{ preview.data.ruleCode }}</el-descriptions-item>
        <el-descriptions-item label="规则名称">{{ preview.data.name }}</el-descriptions-item>
        <el-descriptions-item label="编码模式">{{ preview.data.pattern }}</el-descriptions-item>
        <el-descriptions-item label="参考示例">{{ preview.data.example || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下一编码">
          <span class="num" style="color: #185fa5; font-weight: 700; font-size: 15px">{{ preview.data.preview }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="下一流水号"><span class="num">{{ preview.data.nextSeq }}</span></el-descriptions-item>
      </el-descriptions>
      <el-alert type="info" :closable="false" show-icon style="margin-top: 12px"
        title="预览使用当前计数器值+1演示，不会消耗流水号；上下文占位符预览时为空" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { codeRulePage, codeRulePreview, codeRuleCreate, codeRuleUpdate, codeRuleDelete } from '../../../api/mdm'

const loading = ref(false)
const rows = ref([])
const query = reactive({ kw: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const load = async () => {
  loading.value = true
  try {
    const data = await codeRulePage({
      current: page.current, size: page.size,
      kw: query.kw || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.kw = ''
  page.current = 1
  load()
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  pattern: [{ required: true, message: '请输入编码模式', trigger: 'blur' }]
}

const openDialog = row => {
  dlg.form = row ? { ...row } : { ruleCode: '', name: '', pattern: '', example: '', remark: '' }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    if (dlg.form.id) {
      await codeRuleUpdate(dlg.form)
      ElMessage.success('修改成功')
    } else {
      await codeRuleCreate(dlg.form)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除编码规则「${row.ruleCode} ${row.name}」吗？被业务引用的规则删除后可能导致无法生成编码。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await codeRuleDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

// ================= 预览 =================
const preview = reactive({ visible: false, data: {} })
const onPreview = async row => {
  preview.data = await codeRulePreview(row.ruleCode)
  preview.visible = true
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
.form-tip { font-size: 12px; color: #6b7280; line-height: 1.6; margin-top: 4px; }
</style>
