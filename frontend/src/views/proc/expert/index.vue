<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="专业">
            <el-input v-model="query.specialty" placeholder="专业领域" clearable style="width: 160px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增专家</el-button>
          <el-button type="success" icon="MagicStick" @click="extract.visible = true">抽取专家</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="phone" label="电话" width="150" show-overflow-tooltip />
        <el-table-column prop="specialty" label="专业领域" min-width="160" show-overflow-tooltip />
        <el-table-column prop="avoidOrgs" label="回避单位" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.avoidOrgs || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status, statusOverrides)[1]" size="small">
              {{ statusMeta(row.status, statusOverrides)[0] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑专家' : '新增专家'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.data.name" placeholder="专家姓名" maxlength="60" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.data.phone" placeholder="联系电话" maxlength="30" />
        </el-form-item>
        <el-form-item label="专业领域">
          <el-input v-model="form.data.specialty" placeholder="如：建筑工程、造价、安装" maxlength="120" />
        </el-form-item>
        <el-form-item label="回避单位">
          <el-input v-model="form.data.avoidOrgs" type="textarea" :rows="2" placeholder="需回避的单位，逗号分隔（抽取时排除含关键词的专家）" maxlength="250" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.data.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 抽取专家 -->
    <el-dialog v-model="extract.visible" title="随机抽取评标专家" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="抽取数量">
          <el-input-number v-model="extract.count" :min="1" :max="20" :step="1" />
        </el-form-item>
        <el-form-item label="回避关键词">
          <el-input v-model="extract.avoidKeyword" placeholder="如：某供应商名称（排除回避单位含此词的专家）" maxlength="60" />
        </el-form-item>
      </el-form>
      <div class="block-title">抽取结果</div>
      <el-table :data="extract.result" size="small" border v-if="extract.result.length">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="specialty" label="专业领域" min-width="140" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="140" show-overflow-tooltip />
      </el-table>
      <el-empty v-else description="暂无抽取结果（点击「开始抽取」）" :image-size="60" />
      <template #footer>
        <el-button @click="extract.visible = false">关闭</el-button>
        <el-button type="primary" :loading="extract.loading" @click="onExtract">开始抽取</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  fetchExperts, createExpert, updateExpert, deleteExpert, extractExperts, statusMeta
} from '../../../api/proc'

const statusOverrides = { INACTIVE: ['停用', 'info'] }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, specialty: '', status: '' })

const load = async () => {
  loading.value = true
  try {
    const page = await fetchExperts(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.specialty = ''; query.status = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const rules = { name: [{ required: true, message: '请输入专家姓名', trigger: 'blur' }] }
const openForm = row => {
  form.id = row?.id || null
  form.data = {
    name: row?.name || '', phone: row?.phone || '', specialty: row?.specialty || '',
    avoidOrgs: row?.avoidOrgs || '', status: row?.status || 'ACTIVE'
  }
  form.visible = true
}
const onSave = async () => {
  await formRef.value.validate()
  form.saving = true
  try {
    if (form.id) await updateExpert({ id: form.id, ...form.data })
    else await createExpert(form.data)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除专家【${row.name}】？`, '删除', { type: 'warning' })
  await deleteExpert(row.id)
  ElMessage.success('已删除')
  load()
}

// ============ 抽取 ============
const extract = reactive({ visible: false, loading: false, count: 3, avoidKeyword: '', result: [] })
const onExtract = async () => {
  extract.loading = true
  try {
    const res = await extractExperts({ count: extract.count, avoidKeyword: extract.avoidKeyword || undefined })
    extract.result = res || []
    if (!extract.result.length) ElMessage.warning('无符合回避条件的可用专家')
  } finally {
    extract.loading = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
