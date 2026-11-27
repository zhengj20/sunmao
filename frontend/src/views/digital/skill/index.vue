<template>
  <div class="page-container">
    <div class="mkt-header">
      <div>
        <h2>技能市场</h2>
        <p class="mkt-sub">内置技能 · 外部市场通用技能 · 自定义技能 — 全部可编辑配置（提示词模板 / 取数域 / 说明）</p>
      </div>
      <div class="mkt-toolbar">
        <el-input v-model="kw" placeholder="搜索技能" clearable style="width: 180px" @keyup.enter="loadList" @clear="loadList">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="category" placeholder="全部分类" clearable style="width: 130px" @change="loadList">
          <el-option v-for="(v, k) in SKILL_CATEGORIES" :key="k" :label="v[0]" :value="k" />
        </el-select>
        <el-button type="primary" :icon="Plus" @click="openForm()">新建技能</el-button>
      </div>
    </div>

    <div v-loading="loading" class="mkt-grid">
      <div v-for="s in rows" :key="s.id" class="skill-card" :class="`cat-${s.category}`">
        <div class="skill-top">
          <div class="skill-icon">{{ (s.icon || s.name || 'S').slice(0, 1) }}</div>
          <div class="skill-id">
            <div class="skill-name-row">
              <b>{{ s.name }}</b>
              <el-tag size="small" :type="categoryMeta(s.category)[1]" effect="light">{{ categoryMeta(s.category)[0] }}</el-tag>
            </div>
            <div class="skill-meta">
              <span class="m-chip">{{ dataSourceLabel(s.dataSource) }}</span>
              <span v-if="s.source" class="m-chip src">{{ s.source }}</span>
              <span v-if="skillTools(s).length" class="m-chip tool">🔧 {{ skillTools(s).length }}</span>
            </div>
          </div>
        </div>
        <p class="skill-desc">{{ s.description || '暂无说明' }}</p>
        <div class="skill-tpl" :title="s.promptTemplate">{{ tplPreview(s.promptTemplate) }}</div>
        <div class="skill-foot">
          <span class="muted small">已安装 {{ s.installCount || 0 }} 次</span>
          <div class="skill-btns">
            <el-button link type="primary" size="small" :icon="Download" @click="openInstall(s)">安装</el-button>
            <el-button link size="small" :icon="EditPen" @click="openForm(s)">编辑</el-button>
            <el-button v-if="s.category !== 'BUILTIN'" link type="danger" size="small" :icon="Delete" @click="onDelete(s)">删除</el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !rows.length" description="没有匹配的技能" :image-size="80" />
    </div>

    <!-- 新建/编辑技能（全部字段可编辑） -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑技能' : '新建技能'" width="640px">
      <el-form label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="技能名称" required>
              <el-input v-model="form.name" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="!form.id">
            <el-form-item label="技能编码" required>
              <el-input v-model="form.skillCode" placeholder="如 MY_CUSTOM_SKILL" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="分类">
              <el-select v-model="form.category" style="width: 100%">
                <el-option label="市场通用" value="MARKET" />
                <el-option label="自定义" value="CUSTOM" />
                <el-option label="内置" value="BUILTIN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="取数域">
              <el-select v-model="form.dataSource" style="width: 100%">
                <el-option v-for="d in DATA_SOURCES" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源">
              <el-input v-model="form.source" placeholder="如 SkillHub" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="技能说明">
          <el-input v-model="form.description" maxlength="100" placeholder="一句话说明该技能做什么" />
        </el-form-item>
        <el-form-item label="提示词模板" required>
          <el-input v-model="form.promptTemplate" type="textarea" :rows="6" placeholder="技能执行时使用的提示词模板" />
          <div class="tpl-hint">
            可用占位符：<code>{{ '{' + '{' }}data{{ '}' + '}' }}</code>（执行时自动替换为采集到的真实业务数据）、
            <code>{{ '{' + '{' }}taskName{{ '}' + '}' }}</code>（任务名称）。
          </div>
        </el-form-item>
        <el-form-item label="检索知识库">
          <el-switch v-model="form.kbSearch" :active-value="1" :inactive-value="0" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="绑定工具">
          <el-checkbox-group v-model="form.toolIdsArr" class="tool-bind">
            <el-checkbox v-for="t in tools" :key="t.id" :value="t.id">
              {{ t.name }}<el-tag size="small" :type="t.type === 'BUILTIN' ? 'primary' : 'success'" effect="plain" style="margin-left:4px">{{ t.type === 'BUILTIN' ? '内置' : 'HTTP' }}</el-tag>
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="!tools.length" class="muted small">暂无工具，请到「工具」页新增</div>
        </el-form-item>
        <el-form-item v-if="form.toolIdsArr.length" label="工具参数">
          <el-input v-model="form.toolArgs" type="textarea" :rows="2" placeholder='JSON，按键为工具编码，如 {"calc":"(100+200)*0.5"}' />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 安装到员工 -->
    <el-dialog v-model="installVisible" title="安装到数字员工" width="440px">
      <div class="install-head" v-if="installSkill">
        <b>{{ installSkill.name }}</b>
        <span class="muted small">{{ installSkill.description || '' }}</span>
      </div>
      <el-checkbox-group v-model="installIds" class="install-list">
        <el-checkbox v-for="e in employees" :key="e.id" :value="e.id">{{ e.name }}（{{ roleLabel(e.roleCode) }}）</el-checkbox>
      </el-checkbox-group>
      <div v-if="!employees.length" class="muted small">暂无数字员工，请先到「员工团队」创建</div>
      <template #footer>
        <el-button @click="installVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onInstall">确认安装</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download, EditPen, Delete } from '@element-plus/icons-vue'
import {
  skillList, skillCreate, skillUpdate, skillDelete, skillInstall, empList,
  toolList,
  SKILL_CATEGORIES, DATA_SOURCES, roleLabel, categoryMeta, dataSourceLabel
} from '../../../api/digital'

const loading = ref(false)
const saving = ref(false)
const kw = ref('')
const category = ref('')
const rows = ref([])
const employees = ref([])
const tools = ref([])

const loadTools = async () => { tools.value = (await toolList()) || [] }
/** 技能已绑定工具（名称列表） */
const skillTools = (s) => {
  if (!s.toolIds) return []
  const ids = String(s.toolIds).split(',').map(x => Number(x.trim())).filter(Boolean)
  return tools.value.filter(t => ids.includes(t.id))
}

const loadList = async () => {
  loading.value = true
  try {
    rows.value = (await skillList({ kw: kw.value || undefined, category: category.value || undefined })) || []
  } finally { loading.value = false }
}

// ================= 表单 =================
const formVisible = ref(false)
const empty = () => ({ id: null, skillCode: '', name: '', category: 'CUSTOM', dataSource: 'NONE', icon: '', description: '', promptTemplate: '', source: '', kbSearch: 1, toolIdsArr: [], toolArgs: '', status: 'ACTIVE' })
const form = reactive(empty())
const openForm = s => {
  Object.assign(form, empty())
  if (s) {
    Object.assign(form, {
      id: s.id, skillCode: s.skillCode, name: s.name, category: s.category,
      dataSource: s.dataSource, icon: s.icon, description: s.description,
      promptTemplate: s.promptTemplate, source: s.source, kbSearch: s.kbSearch ?? 1, status: s.status,
      toolIdsArr: s.toolIds ? String(s.toolIds).split(',').map(x => Number(x.trim())).filter(Boolean) : [],
      toolArgs: s.toolArgs || ''
    })
  }
  formVisible.value = true
}
const onSave = async () => {
  if (!form.name) return ElMessage.warning('请填写技能名称')
  if (!form.promptTemplate) return ElMessage.warning('请填写提示词模板')
  saving.value = true
  try {
    const toolPayload = {
      toolIds: form.toolIdsArr.length ? form.toolIdsArr.join(',') : null,
      toolArgs: (form.toolArgs || '').trim() || null
    }
    if (form.id) {
      await skillUpdate(form.id, {
        name: form.name, category: form.category, dataSource: form.dataSource,
        icon: form.icon, description: form.description, promptTemplate: form.promptTemplate,
        source: form.source, kbSearch: form.kbSearch, status: form.status, ...toolPayload
      })
    } else {
      await skillCreate({
        skillCode: form.skillCode, name: form.name, category: form.category,
        dataSource: form.dataSource, icon: form.icon, description: form.description,
        promptTemplate: form.promptTemplate, source: form.source, kbSearch: form.kbSearch, status: form.status, ...toolPayload
      })
    }
    ElMessage.success('已保存')
    formVisible.value = false
    loadList()
  } finally { saving.value = false }
}
const onDelete = async s => {
  await ElMessageBox.confirm(`确认删除技能「${s.name}」？已装配该技能的员工将同步移除。`, '提示', { type: 'warning' })
  await skillDelete(s.id)
  ElMessage.success('已删除')
  loadList()
}

// ================= 安装 =================
const installVisible = ref(false)
const installSkill = ref(null)
const installIds = ref([])
const openInstall = async s => {
  installSkill.value = s
  installIds.value = []
  installVisible.value = true
  if (!employees.value.length) employees.value = (await empList()) || []
}
const onInstall = async () => {
  if (!installIds.value.length) return ElMessage.warning('请选择员工')
  saving.value = true
  try {
    await skillInstall(installSkill.value.id, installIds.value)
    ElMessage.success('已安装')
    installVisible.value = false
    loadList()
  } finally { saving.value = false }
}

const tplPreview = t => {
  if (!t) return ''
  const plain = t.replace(/\{\{[\s\S]*?\}\}/g, '▦')
  return plain.length > 60 ? plain.slice(0, 60) + '…' : plain
}

onMounted(() => { loadList(); loadTools() })
</script>

<style scoped>
.mkt-header {
  display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 10px;
  padding: 6px 4px 14px;
}
.mkt-header h2 { margin: 0; font-size: 19px; color: #0c2d4e; }
.mkt-sub { margin: 6px 0 0; font-size: 12.5px; color: #7b8a9c; }
.mkt-toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

.mkt-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(330px, 1fr)); gap: 14px;
}
.skill-card {
  background: #fff; border: 1px solid #e3ecf5; border-radius: 12px; padding: 16px;
  display: flex; flex-direction: column; gap: 10px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.skill-card:hover { transform: translateY(-2px); box-shadow: 0 8px 22px rgba(12, 45, 78, 0.1); }
.skill-card.cat-BUILTIN { border-top: 3px solid #185fa5; }
.skill-card.cat-MARKET { border-top: 3px solid #2fa36b; }
.skill-card.cat-CUSTOM { border-top: 3px solid #e8a33d; }

.skill-top { display: flex; gap: 12px; align-items: flex-start; }
.skill-icon {
  flex: none; width: 42px; height: 42px; border-radius: 12px; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 19px; font-weight: 800;
  background: linear-gradient(135deg, #8aadd2, #185fa5);
}
.skill-card.cat-MARKET .skill-icon { background: linear-gradient(135deg, #7fd4a8, #0f8a5f); }
.skill-card.cat-CUSTOM .skill-icon { background: linear-gradient(135deg, #f2c684, #d98a2b); }
.skill-id { min-width: 0; }
.skill-name-row { display: flex; align-items: center; gap: 8px; }
.skill-name-row b { font-size: 14.5px; color: #12293f; }
.skill-meta { display: flex; gap: 6px; margin-top: 7px; flex-wrap: wrap; }
.m-chip {
  font-size: 11px; padding: 2px 8px; border-radius: 999px;
  background: #f0f5fa; color: #5c6b7c; border: 1px solid #e0eaf3;
}
.m-chip.src { background: #e9f7ef; color: #1d7a4c; border-color: #c4e6d2; }
.m-chip.tool { background: #eef3fb; color: #185fa5; border-color: #cddcf0; }
.tool-bind { display: flex; flex-direction: column; gap: 6px; }

.skill-desc { margin: 0; font-size: 12.5px; color: #7b8a9c; line-height: 1.65; min-height: 40px; }
.skill-tpl {
  font-size: 11.5px; color: #a5b2c0; background: #fafbfd; border: 1px dashed #e3ecf5;
  border-radius: 8px; padding: 7px 10px; line-height: 1.6;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.skill-foot { display: flex; justify-content: space-between; align-items: center; margin-top: auto; }
.skill-btns { display: flex; gap: 2px; }

.tpl-hint { margin-top: 6px; font-size: 12px; color: #7b8a9c; }
.tpl-hint code { background: #eef5fb; color: #185fa5; padding: 1px 5px; border-radius: 4px; }

.install-head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.install-list { display: flex; flex-direction: column; gap: 6px; }
.install-list .el-checkbox { margin-right: 0; height: auto; }
.muted { color: #909399; }
.small { font-size: 12px; }
</style>
