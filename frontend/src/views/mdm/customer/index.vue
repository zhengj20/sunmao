<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="客户名称/编号/证件号" clearable style="width: 220px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item label="客户类型">
            <el-select v-model="query.customerType" placeholder="全部" clearable style="width: 130px" @change="load">
              <el-option label="个人" value="PERSON" />
              <el-option label="企业" value="ENTERPRISE" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增客户</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border style="width: 100%">
        <el-table-column prop="customerCode" label="客户编号" width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.customerType === 'ENTERPRISE' ? 'warning' : ''" size="small">
              {{ row.customerType === 'ENTERPRISE' ? '企业' : '个人' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="证件类型" width="100" align="center">
          <template #default="{ row }">{{ CERT_TYPE[row.certType] || row.certType }}</template>
        </el-table-column>
        <el-table-column prop="certNo" label="证件号" width="190" show-overflow-tooltip />
        <el-table-column prop="mobile" label="手机号" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.address || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <!-- 新增/编辑客户 -->
    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑客户' : '新增客户'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="客户编号" v-if="dlg.form.id">
              <el-input v-model="dlg.form.customerCode" disabled />
            </el-form-item>
            <el-form-item label="客户名称" prop="name">
              <el-input v-model="dlg.form.name" placeholder="个人或企业名称" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户类型" prop="customerType">
              <el-radio-group v-model="dlg.form.customerType">
                <el-radio value="PERSON">个人</el-radio>
                <el-radio value="ENTERPRISE">企业</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证件类型" prop="certType">
              <el-select v-model="dlg.form.certType" style="width: 100%">
                <el-option v-for="(label, value) in CERT_TYPE" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证件号" prop="certNo">
              <el-input v-model="dlg.form.certNo" placeholder="证件号码" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="dlg.form.mobile" placeholder="请输入手机号" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址" prop="address">
              <el-input v-model="dlg.form.address" placeholder="联系地址" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="dlg.form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 客户详情（含关联人） -->
    <el-drawer v-model="drawer.visible" size="56%" :title="`客户详情 - ${drawer.data?.name || ''}`">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="客户编号">{{ drawer.data?.customerCode }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ drawer.data?.name }}</el-descriptions-item>
        <el-descriptions-item label="客户类型">{{ drawer.data?.customerType === 'ENTERPRISE' ? '企业' : '个人' }}</el-descriptions-item>
        <el-descriptions-item label="证件类型">{{ CERT_TYPE[drawer.data?.certType] || drawer.data?.certType }}</el-descriptions-item>
        <el-descriptions-item label="证件号" :span="2">{{ drawer.data?.certNo }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ drawer.data?.mobile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ drawer.data?.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ drawer.data?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ fmtTime(drawer.data?.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <div class="tab-toolbar" style="margin-top: 16px">
        <span class="tab-title">关联人（共同购买人/配偶/担保人）</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openRelDialog()">新增关联人</el-button>
      </div>
      <el-table :data="relatives" v-loading="drawer.loading" border size="small">
        <el-table-column prop="name" label="关联人姓名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="certNo" label="证件号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.certNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="relation" label="关系" width="110" align="center">
          <template #default="{ row }">{{ row.relation || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRelDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onRelDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 关联人弹窗 -->
    <el-dialog v-model="relDlg.visible" :title="relDlg.form.id ? '编辑关联人' : '新增关联人'" width="480px" destroy-on-close>
      <el-form ref="relFormRef" :model="relDlg.form" :rules="relRules" label-width="100px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="relDlg.form.name" placeholder="关联人姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="证件号" prop="certNo">
          <el-input v-model="relDlg.form.certNo" placeholder="证件号码" maxlength="32" />
        </el-form-item>
        <el-form-item label="关系" prop="relation">
          <el-select v-model="relDlg.form.relation" placeholder="请选择关系" allow-create filterable style="width: 100%">
            <el-option v-for="r in RELATIONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="relDlg.form.phone" placeholder="联系电话" maxlength="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="relDlg.saving" @click="onRelSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  customerPage, customerDetail, customerCreate, customerUpdate, customerDelete,
  relativeList, relativeCreate, relativeUpdate, relativeDelete
} from '../../../api/mdm'
import { fmtTime } from '../../../utils/format'

const CERT_TYPE = { ID_CARD: '身份证', PASSPORT: '护照', BIZ_LICENSE: '营业执照' }
const RELATIONS = ['共同购买人', '配偶', '担保人']

const loading = ref(false)
const rows = ref([])
const query = reactive({ kw: '', customerType: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const load = async () => {
  loading.value = true
  try {
    const data = await customerPage({
      current: page.current, size: page.size,
      kw: query.kw || undefined,
      customerType: query.customerType || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.kw = ''
  query.customerType = ''
  page.current = 1
  load()
}

// ================= 客户弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  certNo: [{ required: true, message: '请输入证件号', trigger: 'blur' }],
  mobile: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

const openDialog = async row => {
  if (row) {
    const detail = await customerDetail(row.id)
    dlg.form = { ...detail }
  } else {
    dlg.form = {
      name: '', certType: 'ID_CARD', certNo: '', customerType: 'PERSON',
      mobile: '', address: '', remark: ''
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    if (dlg.form.id) {
      await customerUpdate(dlg.form)
      ElMessage.success('修改成功')
    } else {
      await customerCreate(dlg.form)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除客户「${row.name}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await customerDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

// ================= 详情与关联人 =================
const drawer = ref({ visible: false, loading: false, data: {} })
const relatives = ref([])

const openDetail = async row => {
  drawer.value.data = await customerDetail(row.id)
  drawer.value.visible = true
  loadRelatives()
}

const loadRelatives = async () => {
  drawer.value.loading = true
  try {
    relatives.value = await relativeList(drawer.value.data.id) || []
  } finally {
    drawer.value.loading = false
  }
}

const relFormRef = ref()
const relDlg = reactive({ visible: false, saving: false, form: {} })
const relRules = {
  name: [{ required: true, message: '请输入关联人姓名', trigger: 'blur' }],
  relation: [{ required: true, message: '请选择关系', trigger: 'change' }]
}

const openRelDialog = row => {
  relDlg.form = row ? { ...row } : { name: '', certNo: '', relation: '共同购买人', phone: '' }
  relDlg.visible = true
}

const onRelSave = async () => {
  await relFormRef.value.validate()
  relDlg.saving = true
  try {
    const cid = drawer.value.data.id
    if (relDlg.form.id) await relativeUpdate(cid, relDlg.form.id, relDlg.form)
    else await relativeCreate(cid, relDlg.form)
    ElMessage.success('保存成功')
    relDlg.visible = false
    loadRelatives()
  } finally {
    relDlg.saving = false
  }
}

const onRelDelete = row => {
  ElMessageBox.confirm(`确定删除关联人「${row.name}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await relativeDelete(drawer.value.data.id, row.id)
      ElMessage.success('删除成功')
      loadRelatives()
    }).catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
.tab-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.tab-toolbar .tab-title { font-weight: 600; color: #0c447c; margin-right: auto; }
</style>
