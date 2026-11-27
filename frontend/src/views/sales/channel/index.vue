<template>
  <div class="page-container channel">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.channelType" placeholder="渠道类型" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="t in CHANNEL_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="v in Object.keys(CHANNEL_STATUS)" :key="v" :label="CHANNEL_STATUS[v][0]" :value="v" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增渠道</el-button>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- 渠道带客成交分析 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">渠道来访结构（案场带客）</span></template>
            <ChartBox v-if="channel.visitByChannel?.length" :option="visitOption" height="280px" />
            <el-empty v-else description="暂无来访渠道数据" :image-size="70" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">渠道成交结构（中介带客签约）</span></template>
            <ChartBox v-if="channel.contractBySupplier?.length" :option="dealOption" height="280px" />
            <el-empty v-else description="暂无中介成交数据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 渠道列表 -->
      <el-card shadow="never" class="table-card">
        <template #header><span class="card-title">渠道名录</span></template>
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column prop="channelName" label="渠道名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="渠道类型" width="120" align="center">
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.channelType || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="contactName" label="对接人" width="130" align="center"><template #default="{ row }">{{ row.contactName || '-' }}</template></el-table-column>
          <el-table-column label="佣金率" width="110" align="right">
            <template #default="{ row }">{{ row.commissionRate == null ? '-' : (Number(row.commissionRate) * 100).toFixed(2) + '%' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }"><el-tag :type="statusMeta(row.status, CHANNEL_STATUS)[1]" size="small">{{ statusMeta(row.status, CHANNEL_STATUS)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
            :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
            @current-change="load" @size-change="onSearch"
          />
        </div>
      </el-card>

      <!-- 新增/编辑弹窗 -->
      <el-dialog v-model="form.visible" :title="form.id ? '编辑渠道' : '新增渠道'" width="540px">
        <el-form :model="form" label-width="100px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="渠道名称" required><el-input v-model="form.channelName" maxlength="100" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="渠道类型">
                <el-select v-model="form.channelType" style="width: 100%">
                  <el-option v-for="t in CHANNEL_TYPES" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="对接人"><el-input v-model="form.contactName" maxlength="60" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="佣金率(%)">
                <el-input-number v-model="form.commissionPct" :controls="false" :min="0" :max="100" :precision="2" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态">
                <el-select v-model="form.status" style="width: 100%">
                  <el-option label="生效" value="ACTIVE" />
                  <el-option label="停用" value="INACTIVE" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="form.visible = false">取消</el-button>
          <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { wan } from '../../../utils/format'
import {
  fetchProjectOptions, channelPage, channelCreate, channelUpdate, channelDelete,
  dashboardChannel, CHANNEL_TYPES, CHANNEL_STATUS, statusMeta
} from '../../../api/sales'
import ChartBox from '../../../components/ChartBox.vue'

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const channel = ref({})
const query = reactive({ channelType: null, status: null, current: 1, size: 20 })

const load = async () => {
  if (!projectId.value) { rows.value = []; return }
  loading.value = true
  try {
    const [page, ch] = await Promise.all([
      channelPage({
        current: query.current, size: query.size, projectId: projectId.value,
        channelType: query.channelType || undefined, status: query.status || undefined
      }),
      dashboardChannel(projectId.value).catch(() => ({}))
    ])
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
    channel.value = ch || {}
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; load() }

const visitOption = computed(() => ({
  color: ['#185fa5', '#5687ba', '#8aadd2', '#67c23a', '#e6a23c', '#909399'],
  tooltip: { trigger: 'item', formatter: '{b}: {c} 组 ({d}%)' },
  legend: { bottom: 0, type: 'scroll' },
  series: [{
    type: 'pie', radius: ['42%', '70%'], center: ['50%', '46%'],
    itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
    label: { show: false },
    data: (channel.value.visitByChannel || []).map(c => ({ name: c.channel, value: Number(c.cnt) }))
  }]
}))

const dealOption = computed(() => {
  const data = channel.value.contractBySupplier || []
  return {
    color: ['#185fa5'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    grid: { left: 100, right: 30, top: 20, bottom: 20 },
    xAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    yAxis: { type: 'category', data: data.map(d => d.channel) },
    series: [{ name: '成交额', type: 'bar', barWidth: 18, data: data.map(d => Number(d.amt)), label: { show: true, position: 'right', formatter: p => wan(p.value) } }]
  }
})

const form = reactive({
  visible: false, saving: false, id: null, channelName: '', channelType: '中介',
  contactName: '', commissionPct: null, status: 'ACTIVE'
})
const openForm = row => {
  Object.assign(form, {
    visible: true, saving: false, id: row?.id || null,
    channelName: row?.channelName || '', channelType: row?.channelType || '中介',
    contactName: row?.contactName || '',
    commissionPct: row?.commissionRate != null ? Number((Number(row.commissionRate) * 100).toFixed(2)) : null,
    status: row?.status || 'ACTIVE'
  })
}
const onSave = async () => {
  if (!form.channelName) return ElMessage.warning('请填写渠道名称')
  form.saving = true
  try {
    const payload = {
      id: form.id, projectId: projectId.value, channelName: form.channelName, channelType: form.channelType,
      contactName: form.contactName, commissionRate: form.commissionPct != null ? Number(form.commissionPct) / 100 : null,
      status: form.status
    }
    if (form.id) { await channelUpdate(payload); ElMessage.success('已更新') }
    else { await channelCreate(payload); ElMessage.success('已创建') }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除渠道【${row.channelName}】？`, '提示', { type: 'warning' })
  await channelDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    load()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.section-row { margin-bottom: 12px; }
.table-card { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
