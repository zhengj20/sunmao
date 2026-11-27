<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="名称/编号/信用代码" clearable style="width: 200px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item label="库别">
            <el-select v-model="query.libType" placeholder="全部" clearable style="width: 130px" @change="load">
              <el-option v-for="(label, value) in LIB_TYPE" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="warning" plain :icon="Bell" @click="openExpiring">资质到期提醒</el-button>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增供应商</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border style="width: 100%">
        <el-table-column prop="supplierCode" label="供应商编号" width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="供应商名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="shortName" label="简称" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.shortName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="creditCode" label="统一社会信用代码" width="180" show-overflow-tooltip />
        <el-table-column prop="classify" label="分类" width="80" align="center">
          <template #default="{ row }">{{ row.classify || '-' }}</template>
        </el-table-column>
        <el-table-column label="品类" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryMap[row.categoryId] || '-' }}</template>
        </el-table-column>
        <el-table-column label="资质等级" width="85" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.grade" :type="GRADE_TAG[row.grade] || 'info'" size="small">{{ row.grade }}级</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="库别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="LIB_TYPE_TAG[row.libType] || 'info'" class="status-tag">{{ LIB_TYPE[row.libType] || row.libType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="履约评分" width="90" align="right" class="num">
          <template #default="{ row }">
            <span :style="scoreStyle(row.performanceScore)">{{ row.performanceScore ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="注册资本(万元)" width="115" align="right" class="num">
          <template #default="{ row }">{{ money(row.registeredCapital) }}</template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contactPerson || '-' }}</template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contactPhone || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.libType !== 'BLACKLIST'" link type="danger" @click="onBlacklist(row)">拉黑</el-button>
            <el-button v-else link type="success" @click="onRestore(row)">恢复</el-button>
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

    <!-- 新增/编辑供应商 -->
    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑供应商' : '新增供应商'" width="720px" destroy-on-close top="4vh">
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="130px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商编号" v-if="dlg.form.id">
              <el-input v-model="dlg.form.supplierCode" disabled />
            </el-form-item>
            <el-form-item label="供应商名称" prop="name">
              <el-input v-model="dlg.form.name" placeholder="请输入供应商名称" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="简称" prop="shortName">
              <el-input v-model="dlg.form.shortName" placeholder="请输入简称" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统一社会信用代码" prop="creditCode">
              <el-input v-model="dlg.form.creditCode" placeholder="18位统一社会信用代码" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="classify">
              <el-select v-model="dlg.form.classify" placeholder="请选择分类" clearable style="width: 100%">
                <el-option v-for="c in CLASSIFIES" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品类分类" prop="categoryId">
              <el-tree-select
                v-model="dlg.form.categoryId"
                :data="purchaseTree"
                node-key="id"
                :props="{ label: 'name', children: 'children' }"
                check-strictly
                clearable
                default-expand-all
                placeholder="采购品类"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资质等级" prop="grade">
              <el-select v-model="dlg.form.grade" placeholder="A/B/C/D" clearable style="width: 100%">
                <el-option v-for="g in ['A', 'B', 'C', 'D']" :key="g" :label="g + '级'" :value="g" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库别" prop="libType">
              <el-select v-model="dlg.form.libType" style="width: 100%">
                <el-option v-for="(label, value) in LIB_TYPE" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="法定代表人" prop="legalPerson">
              <el-input v-model="dlg.form.legalPerson" placeholder="请输入法定代表人" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="注册资本(万元)" prop="registeredCapital">
              <el-input-number v-model="dlg.form.registeredCapital" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="履约评分" prop="performanceScore">
              <el-input-number v-model="dlg.form.performanceScore" :min="0" :max="100" :precision="2" :controls="false" placeholder="0-100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="dlg.form.contactPerson" placeholder="请输入联系人" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="dlg.form.contactPhone" placeholder="请输入联系电话" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址" prop="address">
              <el-input v-model="dlg.form.address" placeholder="请输入注册地址" maxlength="255" />
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

    <!-- 供应商详情（含资质证照） -->
    <el-drawer v-model="drawer.visible" size="62%" :title="`供应商详情 - ${drawer.data?.name || ''}`">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="供应商编号">{{ drawer.data?.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="供应商名称" :span="2">{{ drawer.data?.name }}</el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码" :span="2">{{ drawer.data?.creditCode }}</el-descriptions-item>
        <el-descriptions-item label="库别">
          <el-tag :type="LIB_TYPE_TAG[drawer.data?.libType] || 'info'">{{ LIB_TYPE[drawer.data?.libType] || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="分类">{{ drawer.data?.classify || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品类">{{ categoryMap[drawer.data?.categoryId] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资质等级">
          <el-tag v-if="drawer.data?.grade" :type="GRADE_TAG[drawer.data.grade] || 'info'">{{ drawer.data.grade }}级</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="法定代表人">{{ drawer.data?.legalPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册资本(万元)"><span class="num">{{ money(drawer.data?.registeredCapital) }}</span></el-descriptions-item>
        <el-descriptions-item label="履约评分"><span class="num" :style="scoreStyle(drawer.data?.performanceScore)">{{ drawer.data?.performanceScore ?? '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="联系人">{{ drawer.data?.contactPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ drawer.data?.contactPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ drawer.data?.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="拉黑原因" :span="3">
          <span v-if="drawer.data?.libType === 'BLACKLIST'" style="color: #f56c6c">{{ drawer.data?.blacklistReason || '-' }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ drawer.data?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="tab-toolbar" style="margin-top: 16px">
        <span class="tab-title">资质证照</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openQualDialog()">新增资质</el-button>
      </div>
      <el-table :data="quals" v-loading="drawer.loading" border size="small">
        <el-table-column prop="qualType" label="证照类型" width="130" show-overflow-tooltip />
        <el-table-column prop="qualNo" label="证照编号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.qualNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="issueDate" label="发证日期" width="105" align="center" />
        <el-table-column label="到期日期" width="105" align="center">
          <template #default="{ row }">
            <span :style="expireStyle(row.expireDate)">{{ row.expireDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="OCR识别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.ocrStatus === 1 ? 'success' : 'info'" size="small">{{ row.ocrStatus === 1 ? '已识别' : '未识别' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openQualDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onQualDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 资质证照弹窗 -->
    <el-dialog v-model="qualDlg.visible" :title="qualDlg.form.id ? '编辑资质证照' : '新增资质证照'" width="520px" destroy-on-close>
      <el-form ref="qualFormRef" :model="qualDlg.form" :rules="qualRules" label-width="100px">
        <el-form-item label="证照类型" prop="qualType">
          <el-select v-model="qualDlg.form.qualType" placeholder="请选择证照类型" allow-create filterable style="width: 100%">
            <el-option v-for="t in QUAL_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="证照编号" prop="qualNo">
          <el-input v-model="qualDlg.form.qualNo" placeholder="请输入证照编号" maxlength="64" />
        </el-form-item>
        <el-form-item label="发证日期" prop="issueDate">
          <el-date-picker v-model="qualDlg.form.issueDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到期日期" prop="expireDate">
          <el-date-picker v-model="qualDlg.form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qualDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="qualDlg.saving" @click="onQualSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 到期提醒 -->
    <el-dialog v-model="expiring.visible" title="资质到期提醒（90天内）" width="720px">
      <el-table :data="expiring.rows" v-loading="expiring.loading" border size="small" max-height="420">
        <el-table-column prop="supplierCode" label="供应商编号" width="120" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="qualType" label="证照类型" width="120" show-overflow-tooltip />
        <el-table-column prop="qualNo" label="证照编号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.qualNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="到期日期" width="110" align="center">
          <template #default="{ row }">
            <span style="color: #e6a23c; font-weight: 600">{{ row.expireDate }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!expiring.loading && !expiring.rows.length" description="暂无90天内到期的资质证照" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Bell } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  supplierPage, supplierDetail, supplierCreate, supplierUpdate, supplierDelete,
  supplierBlacklist, supplierRestore,
  qualificationList, qualificationCreate, qualificationUpdate, qualificationDelete, qualificationExpiring,
  categoryTree
} from '../../../api/mdm'
import { money } from '../../../utils/format'

const LIB_TYPE = { OFFICIAL: '正式库', PRESELECT: '预选库', BLACKLIST: '黑名单' }
const LIB_TYPE_TAG = { OFFICIAL: 'success', PRESELECT: '', BLACKLIST: 'danger' }
const GRADE_TAG = { A: 'success', B: '', C: 'warning', D: 'danger' }
const CLASSIFIES = ['工程', '材料', '服务', '设备']
const QUAL_TYPES = ['营业执照', '资质证书', '安全生产许可证']

const loading = ref(false)
const rows = ref([])
const query = reactive({ kw: '', libType: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const purchaseTree = ref([])
const categoryMap = ref({})

const flattenCat = (list, map) => (list || []).forEach(c => {
  map[c.id] = c.name
  flattenCat(c.children, map)
})

const load = async () => {
  loading.value = true
  try {
    const data = await supplierPage({
      current: page.current, size: page.size,
      kw: query.kw || undefined,
      libType: query.libType || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.kw = ''
  query.libType = ''
  page.current = 1
  load()
}

const scoreStyle = v => {
  if (v === null || v === undefined) return {}
  if (v >= 90) return { color: '#67c23a', fontWeight: 700 }
  if (v >= 70) return { color: '#185fa5', fontWeight: 600 }
  return { color: '#f56c6c', fontWeight: 600 }
}

const expireStyle = d => {
  if (!d) return {}
  const days = dayjs(d).diff(dayjs(), 'day')
  if (days < 0) return { color: '#f56c6c', fontWeight: 700 }
  if (days <= 90) return { color: '#e6a23c', fontWeight: 600 }
  return {}
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  creditCode: [{ required: true, message: '请输入统一社会信用代码', trigger: 'blur' }]
}

const openDialog = async row => {
  if (row) {
    const detail = await supplierDetail(row.id)
    dlg.form = { ...detail }
  } else {
    dlg.form = {
      name: '', shortName: '', creditCode: '', categoryId: null, classify: '', grade: '',
      libType: 'PRESELECT', legalPerson: '', registeredCapital: null,
      contactPerson: '', contactPhone: '', address: '', performanceScore: null, remark: ''
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    if (dlg.form.id) {
      await supplierUpdate(dlg.form)
      ElMessage.success('修改成功')
    } else {
      await supplierCreate(dlg.form)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除供应商「${row.name}」吗？已被合同等业务引用的供应商禁止删除。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await supplierDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

const onBlacklist = row => {
  ElMessageBox.prompt(`将供应商「${row.name}」加入黑名单，请输入拉黑原因：`, '拉黑确认', {
    type: 'warning',
    inputPlaceholder: '如：重大质量事故 / 严重违约',
    inputValidator: v => (v && v.trim()) ? true : '拉黑原因不能为空'
  }).then(async ({ value }) => {
    await supplierBlacklist(row.id, value.trim())
    ElMessage.success('已加入黑名单')
    load()
  }).catch(() => {})
}

const onRestore = row => {
  ElMessageBox.confirm(`确定将供应商「${row.name}」从黑名单恢复到预选库吗？`, '恢复确认', { type: 'info' })
    .then(async () => {
      await supplierRestore(row.id)
      ElMessage.success('已恢复到预选库')
      load()
    }).catch(() => {})
}

// ================= 详情与资质 =================
const drawer = ref({ visible: false, loading: false, data: {} })
const quals = ref([])

const openDetail = async row => {
  drawer.value.data = await supplierDetail(row.id)
  drawer.value.visible = true
  loadQuals()
}

const loadQuals = async () => {
  drawer.value.loading = true
  try {
    quals.value = await qualificationList(drawer.value.data.id) || []
  } finally {
    drawer.value.loading = false
  }
}

const qualFormRef = ref()
const qualDlg = reactive({ visible: false, saving: false, form: {} })
const qualRules = {
  qualType: [{ required: true, message: '请选择/输入证照类型', trigger: 'change' }],
  qualNo: [{ required: true, message: '请输入证照编号', trigger: 'blur' }]
}

const openQualDialog = row => {
  qualDlg.form = row ? { ...row } : { qualType: '营业执照', qualNo: '', issueDate: null, expireDate: null }
  qualDlg.visible = true
}

const onQualSave = async () => {
  await qualFormRef.value.validate()
  qualDlg.saving = true
  try {
    const sid = drawer.value.data.id
    if (qualDlg.form.id) await qualificationUpdate(sid, qualDlg.form.id, qualDlg.form)
    else await qualificationCreate(sid, qualDlg.form)
    ElMessage.success('保存成功')
    qualDlg.visible = false
    loadQuals()
  } finally {
    qualDlg.saving = false
  }
}

const onQualDelete = row => {
  ElMessageBox.confirm(`确定删除资质证照「${row.qualType}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await qualificationDelete(drawer.value.data.id, row.id)
      ElMessage.success('删除成功')
      loadQuals()
    }).catch(() => {})
}

// ================= 到期提醒 =================
const expiring = reactive({ visible: false, loading: false, rows: [] })
const openExpiring = async () => {
  expiring.visible = true
  expiring.loading = true
  try {
    expiring.rows = await qualificationExpiring(90) || []
  } finally {
    expiring.loading = false
  }
}

onMounted(async () => {
  load()
  purchaseTree.value = await categoryTree({ type: 'PURCHASE' }) || []
  const map = {}
  flattenCat(purchaseTree.value, map)
  categoryMap.value = map
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
.tab-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.tab-toolbar .tab-title { font-weight: 600; color: #0c447c; margin-right: auto; }
</style>
