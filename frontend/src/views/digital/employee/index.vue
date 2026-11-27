<template>
  <div class="page-container dig-page">
    <!-- 页头 -->
    <div class="dig-header">
      <div>
        <h2>数字员工团队</h2>
        <p class="dig-sub">招采 · 成本 · 财务 · 营销 — 技能可配置 / 定时·条件·手动触发 / 执行全程留痕</p>
      </div>
      <div class="dig-header-actions">
        <el-tag :type="hasModel ? 'success' : 'info'" size="small">
          {{ hasModel ? '大模型已接入（LLM 模式）' : '规则引擎模式（未启用大模型）' }}
        </el-tag>
        <el-button type="primary" :icon="Plus" @click="openForm()">新增数字员工</el-button>
      </div>
    </div>

    <!-- 员工卡片 -->
    <div v-loading="loading" class="dig-grid">
      <div v-for="b in board" :key="b.employee.id" class="emp-card" :class="`emp-${b.employee.avatar}`">
        <div class="emp-head">
          <div class="emp-avatar-wrap">
            <RoleAvatar :theme="b.employee.avatar" :working="b.running" :size="108" />
            <span class="emp-status" :class="b.running ? 'st-work' : (b.employee.status === 'ACTIVE' ? 'st-idle' : 'st-off')">
              <i class="st-dot"></i>{{ b.running ? '工作中' : (b.employee.status === 'ACTIVE' ? '在岗' : '休假') }}
            </span>
          </div>
          <div class="emp-id">
            <div class="emp-name-row">
              <span class="emp-name">{{ b.employee.name }}</span>
              <el-tag size="small" effect="plain" class="emp-role-tag">{{ roleLabel(b.employee.roleCode) }}</el-tag>
            </div>
            <div class="emp-model">
              <el-icon><Cpu /></el-icon>{{ modelLabel(b.model) }}
            </div>
            <div class="emp-persona">{{ b.employee.persona || b.employee.remark || '未设定人设' }}</div>
          </div>
        </div>

        <div class="emp-skills">
          <span v-for="s in b.skills" :key="s.id" class="skill-chip" :title="s.description">{{ s.name }}</span>
          <span v-if="!b.skills.length" class="skill-chip none">未装配技能</span>
          <el-button link type="primary" size="small" @click="openDrawer(b, 'skills')">装配</el-button>
        </div>

        <div class="emp-stats">
          <div><b>{{ b.tasks.length }}</b><i>任务</i></div>
          <div><b>{{ b.totalRuns || 0 }}</b><i>执行</i></div>
          <div><b>{{ b.successRate == null ? '—' : b.successRate + '%' }}</b><i>成功率</i></div>
          <div><b class="tiny">{{ lastRunText(b) }}</b><i>最近执行</i></div>
        </div>

        <div class="emp-extra-chips">
          <span v-if="schedCount(b)" class="extra-chip sched" :title="'定时任务 ' + schedCount(b) + ' 个，到点自动触发'">⏰ 定时 {{ schedCount(b) }}</span>
          <span v-if="b.employee.kbDocIds" class="extra-chip kb" :title="'已绑定知识库文档 ' + kbCount(b.employee.kbDocIds) + ' 篇'">📚 知识库 {{ kbCount(b.employee.kbDocIds) }}</span>
        </div>

        <!-- 参与的工作流（跨员工协作环节，点击跳转工作流编排） -->
        <div v-if="b.workflows && b.workflows.length" class="emp-wfs">
          <span class="wf-chip" v-for="w in b.workflows" :key="w.wfId" @click="goWorkflow"
                :title="'工作流「' + w.wfName + '」第 ' + w.stepOrder + ' 步：' + w.stepName + '（点击查看工作流编排）'">
            <el-icon><Connection /></el-icon>{{ w.wfName }} · 第{{ w.stepOrder }}步
            <em v-if="w.requiresApproval" class="wf-need-approve">需审批</em>
            <i class="wf-last" :class="'st-' + (w.lastStatus || 'none')">{{ wfLastText(w.lastStatus) }}</i>
          </span>
        </div>

        <div class="emp-actions">
          <el-button type="primary" size="small" :icon="Lightning" :disabled="!b.tasks.length || b.running" @click="openRun(b)">立即执行</el-button>
          <el-button size="small" @click="openDrawer(b, 'tasks')">任务与记录</el-button>
          <el-switch v-model="b.employee.status" active-value="ACTIVE" inactive-value="INACTIVE"
                    inline-prompt active-text="在岗" inactive-text="休假" size="small" class="emp-toggle"
                    @change="onToggleStatus(b)" />
          <el-button link size="small" @click="openForm(b.employee, b)">编辑</el-button>
          <el-button link size="small" type="danger" @click="onDelete(b)">删除</el-button>
        </div>
      </div>

      <el-empty v-if="!loading && !board.length" description="暂无数字员工，点击右上角新增" :image-size="90" />
    </div>

    <!-- 工作流编排任务（员工×技能 流水线接力） -->
    <div class="wfsec">
      <div class="wfsec-head">
        <div>
          <h3 class="wfsec-title">工作流编排任务</h3>
          <p class="wfsec-sub">把「员工 × 技能」串成流水线接力执行 — 环节产物自动注入下一环节，支持人工审批门与整合交付</p>
        </div>
        <el-button @click="goWorkflow">管理编排 →</el-button>
      </div>
      <div v-loading="wfLoading" class="wfsec-grid">
        <div v-for="wf in workflows" :key="wf.workflow.id" class="wbwf-card">
          <div class="wbwf-top">
            <b>{{ wf.workflow.name }}</b>
            <el-tag v-if="wf.workflow.status !== 'ACTIVE'" size="small" type="info" effect="plain">已停用</el-tag>
            <el-tag v-if="wf.workflow.cronExpr" size="small" type="primary" effect="plain">定时 {{ wf.workflow.cronExpr }}</el-tag>
          </div>
          <p class="wbwf-desc">{{ wf.workflow.description || '暂无描述' }}</p>
          <div class="wbwf-chain">
            <template v-for="(s, i) in wf.steps" :key="i">
              <span class="wbwf-chip" :title="(s.step?.name || s.taskName) + '（' + s.employeeName + '）'">
                {{ s.employeeName }}·{{ s.skillName }}
                <i v-if="s.step?.usePrev !== 0" class="wbwf-relay" title="接力上一环节产物">⇠</i>
              </span>
              <span v-if="i < wf.steps.length - 1" class="wbwf-sep">→</span>
            </template>
            <span v-if="!wf.steps.length" class="wbwf-none">未配置环节</span>
          </div>
          <div class="wbwf-foot">
            <el-tag size="small" effect="plain">{{ wf.steps.length }} 环节</el-tag>
            <el-tag size="small" :type="wfRunTag(wf.lastRun?.status)">{{ wfRunText(wf.lastRun?.status) }}</el-tag>
            <span class="wbwf-actions">
              <el-button size="small" type="primary" :disabled="!wf.steps.length" @click="onWfRun(wf)">执行</el-button>
              <el-button size="small" @click="goWorkflow">编排</el-button>
            </span>
          </div>
        </div>
        <div v-if="!workflows.length && !wfLoading" class="wbwf-empty">
          <p>还没有编排好的工作流任务</p>
          <el-button size="small" type="primary" @click="goWorkflow">去编排 →</el-button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑员工 -->
    <el-dialog v-model="formVisible" :title="form.employee.id ? '编辑数字员工' : '新增数字员工'" width="640px">
      <el-form label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.employee.name" placeholder="如 招采专员·招招" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色域" required>
              <el-select v-model="form.employee.roleCode" style="width: 100%" @change="onRoleChange">
                <el-option v-for="r in ROLE_CODES" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="形象">
          <div class="avatar-pick">
            <div v-for="a in AVATARS" :key="a.value" class="avatar-opt" :class="{ on: form.employee.avatar === a.value }" @click="form.employee.avatar = a.value">
              <RoleAvatar :theme="a.value" :size="56" />
              <span>{{ a.label }}</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="绑定模型">
          <el-select v-model="form.employee.modelConfigId" clearable style="width: 100%" placeholder="不选=系统默认激活模型">
            <el-option v-for="m in models" :key="m.id" :label="`${m.provider} / ${m.modelName}${m.status === 'ACTIVE' ? '' : '（停用）'}`" :value="m.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="人设设定">
          <el-input v-model="form.employee.persona" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="如：你是资深房地产成本分析师，精通三算对比与变更签证管控……" />
        </el-form-item>

        <el-form-item label="装配技能">
          <el-select v-model="form.skillIds" multiple style="width: 100%" placeholder="选择该员工可用的技能">
            <el-option v-for="s in allSkills" :key="s.id" :label="s.name" :value="s.id">
              <span>{{ s.name }}</span>
              <span class="opt-cat">{{ categoryMeta(s.category)[0] }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="知识库绑定">
          <el-select v-model="form.kbDocIdsArr" multiple filterable style="width: 100%"
                     placeholder="选择该员工固定引用的知识库文档（执行任务时整篇注入提示词）">
            <el-option v-for="d in kbDocs" :key="d.id" :label="d.title" :value="d.id">
              <span>{{ d.title }}</span>
              <span class="opt-cat">{{ d.category || '未分类' }}</span>
            </el-option>
          </el-select>
          <div v-if="!kbDocs.length" class="muted small">暂无知识库文档，请先到「知识库」添加</div>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.employee.status" style="width: 100%">
                <el-option label="在岗" value="ACTIVE" />
                <el-option label="休假（停用）" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="form.employee.remark" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 立即执行（任务选择） -->
    <el-dialog v-model="runPickVisible" title="选择要执行的任务" width="460px" :close-on-click-modal="false">
      <div v-if="runPickEmp" class="run-pick">
        <div class="run-pick-head">
          <RoleAvatar :theme="runPickEmp.employee.avatar" :size="52" />
          <div>
            <b>{{ runPickEmp.employee.name }}</b>
            <div class="muted small">执行将调用任务绑定的技能并全程留痕</div>
          </div>
        </div>
        <div v-for="t in runPickEmp.tasks" :key="t.id" class="run-pick-item" @click="startRun(t)">
          <div>
            <b>{{ t.name }}</b>
            <div class="muted small">{{ skillName(t.skillId) }} · {{ triggerMeta(t.triggerType)[0] }}触发</div>
          </div>
          <el-icon class="arrow"><ArrowRight /></el-icon>
        </div>
      </div>
      <template #footer>
        <el-button @click="runPickVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 执行过程（实时轮询） -->
    <el-dialog v-model="runLiveVisible" :title="runLiveTitle" width="640px" :close-on-click-modal="false" :show-close="true">
      <div class="run-live">
        <div class="run-live-side">
          <RoleAvatar :theme="runLiveAvatar" :working="runRunning" :size="120" />
          <div class="run-live-mode" v-if="runLive && runLive.aiMode">
            <el-tag :type="runLive.aiMode === 'LLM' ? 'primary' : 'info'" size="small">
              {{ runLive.aiMode === 'LLM' ? 'LLM 大模型' : '规则引擎' }}
            </el-tag>
            <span v-if="runLive.modelName" class="muted small">{{ runLive.modelName }}</span>
          </div>
        </div>
        <div class="run-live-body">
          <RunSteps :steps="runLive ? runLive.steps : ''" />
          <div v-if="runRunning" class="run-thinking">
            <span class="rs-spin-inline"></span>模型推理 / 结果生成中，请稍候…
          </div>
          <div v-if="runLive && !runRunning && runLive.output" class="run-output">
            <div class="run-output-h">执行结果</div>
            <pre>{{ runLive.output }}</pre>
          </div>
          <div v-if="runLive && !runRunning" class="run-live-foot">
            <el-tag :type="runLive.status === 'DONE' ? 'success' : 'danger'" size="small">
              {{ runStatusMeta(runLive.status)[0] }}
            </el-tag>
            <span class="muted small">耗时 {{ fmtDur(runLive.durationMs) }}</span>
            <el-button link type="primary" size="small" @click="goRunCenter">在任务中心查看</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="stopPolling; runLiveVisible = false">{{ runRunning ? '后台继续执行' : '关闭' }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：技能 / 任务 / 运行记录 -->
    <el-drawer v-model="drawerVisible" :title="drawerEmp && drawerEmp.employee.name" size="720px">
      <el-tabs v-model="drawerTab">
        <el-tab-pane label="技能装配" name="skills">
          <div class="drawer-tip muted small">技能全部可编辑配置（提示词模板/取数域/说明），可在「技能市场」修改。</div>
          <el-select v-model="drawerSkillIds" multiple filterable style="width: 100%; margin: 10px 0" placeholder="选择技能">
            <el-option v-for="s in allSkills" :key="s.id" :label="s.name" :value="s.id">
              <span>{{ s.name }}</span>
              <span class="opt-cat">{{ categoryMeta(s.category)[0] }}</span>
            </el-option>
          </el-select>
          <div v-for="s in drawerSkills" :key="s.id" class="skill-row">
            <div class="skill-row-icon" :class="`cat-${s.category}`">{{ (s.icon || 'A')[0] }}</div>
            <div class="skill-row-body">
              <b>{{ s.name }}</b>
              <span class="muted small">{{ dataSourceLabel(s.dataSource) }} · {{ s.description || '无说明' }}</span>
            </div>
            <el-button link type="danger" size="small" @click="uninstallSkill(s)">移除</el-button>
          </div>
          <el-button type="primary" size="small" :loading="saving" style="margin-top: 12px" @click="saveDrawerSkills">保存装配</el-button>
        </el-tab-pane>

        <el-tab-pane label="任务" name="tasks">
          <div class="drawer-tip muted small">定时（CRON）/ 条件触发（真实指标比较）/ 手动执行；任意任务都可随时手动执行。</div>
          <div class="task-row" v-for="t in drawerTasks" :key="t.id">
            <div>
              <b>{{ t.name }}</b>
              <el-tag size="small" :type="triggerMeta(t.triggerType)[1]" style="margin-left: 6px">{{ triggerMeta(t.triggerType)[0] }}</el-tag>
              <div class="muted small">
                {{ skillName(t.skillId) }}
                <template v-if="t.triggerType === 'SCHEDULED'"> · CRON {{ t.cronExpr }} · 下次 {{ t.nextRunAt || '待排' }}</template>
                <template v-else-if="t.triggerType === 'CONDITIONAL'"> · {{ condText(t) }}</template>
                <template v-else> · 仅手动触发</template>
              </div>
              <div class="muted small" v-if="t.lastRunAt">最近执行 {{ t.lastRunAt }}</div>
            </div>
            <div class="task-row-actions">
              <el-button link type="primary" size="small" @click="startRun(t)">执行</el-button>
              <el-button link size="small" @click="openTaskForm(t)">编辑</el-button>
              <el-button link type="danger" size="small" @click="deleteTask(t)">删除</el-button>
            </div>
          </div>
          <el-button type="primary" size="small" icon="Plus" style="margin-top: 12px" @click="openTaskForm()">新增任务</el-button>
        </el-tab-pane>

        <el-tab-pane label="运行记录" name="runs">
          <div class="run-row" v-for="r in drawerRuns" :key="r.id">
            <div>
              <b>{{ r.taskName }}</b>
              <el-tag size="small" :type="runStatusMeta(r.status)[1]" style="margin-left: 6px">{{ runStatusMeta(r.status)[0] }}</el-tag>
              <div class="muted small">{{ r.startedAt }} · {{ triggerMeta(r.triggerType)[0] }}触发 · {{ fmtDur(r.durationMs) }}</div>
            </div>
            <el-button link type="primary" size="small" @click="viewRun(r)">过程</el-button>
          </div>
          <el-empty v-if="!drawerRuns.length" description="暂无运行记录" :image-size="70" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 任务表单 -->
    <el-dialog v-model="taskFormVisible" :title="taskForm.id ? '编辑任务' : '新增任务'" width="560px" append-to-body>
      <el-form label-width="96px">
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.name" maxlength="40" />
        </el-form-item>
        <el-form-item label="所属员工">
          <el-select v-model="taskForm.employeeId" style="width: 100%">
            <el-option v-for="b in board" :key="b.employee.id" :label="b.employee.name" :value="b.employee.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定技能">
          <el-select v-model="taskForm.skillId" style="width: 100%" placeholder="选择员工已装配的技能（也可选任意技能）">
            <el-option v-for="s in allSkills" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发方式" required>
          <el-radio-group v-model="taskForm.triggerType">
            <el-radio-button value="MANUAL">手动</el-radio-button>
            <el-radio-button value="SCHEDULED">定时</el-radio-button>
            <el-radio-button value="CONDITIONAL">条件触发</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="taskForm.triggerType === 'SCHEDULED'" label="CRON" required>
          <div style="width: 100%">
            <el-input v-model="taskForm.cronExpr" placeholder="6 段含秒，如 0 0 9 * * 1-5" />
            <div class="cron-presets">
              <el-tag v-for="p in cronPresets" :key="p.expr" effect="plain" class="cron-preset" @click="taskForm.cronExpr = p.expr">
                {{ p.label }}
              </el-tag>
            </div>
          </div>
        </el-form-item>

        <template v-if="taskForm.triggerType === 'CONDITIONAL'">
          <el-form-item label="条件表达式" required>
            <div class="cond-line">
              <el-select v-model="taskForm.condSource" style="width: 200px">
                <el-option v-for="c in condSourcesList" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
              <el-select v-model="taskForm.condOp" style="width: 88px">
                <el-option label=">=" value=">=" /><el-option label=">" value=">" />
                <el-option label="<=" value="<=" /><el-option label="<" value="<" />
                <el-option label="==" value="==" />
              </el-select>
              <el-input-number v-model="taskForm.condValue" :min="0" :step="1" style="width: 120px" />
            </div>
            <div class="muted small" style="margin-top: 6px">
              例：{{ condSourcesList.find(c => c.value === taskForm.condSource)?.label || '指标' }} {{ taskForm.condOp }} {{ taskForm.condValue }} 时自动执行（基于真实业务数据判断）
            </div>
            <div v-if="condWarn" class="cond-warn">{{ condWarn }}</div>
          </el-form-item>
          <el-form-item label="冷却间隔">
            <el-input-number v-model="taskForm.cooldownMinutes" :min="5" :step="30" style="width: 160px" />
            <span class="muted small">分钟（触发后最小执行间隔，防重复）</span>
          </el-form-item>
        </template>

        <el-form-item label="备注">
          <el-input v-model="taskForm.remark" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运行过程查看（抽屉/弹窗内） -->
    <el-dialog v-model="viewRunVisible" title="执行过程" width="600px" append-to-body>
      <template v-if="viewRunData">
        <div class="view-run-meta">
          <el-tag size="small" :type="runStatusMeta(viewRunData.status)[1]">{{ runStatusMeta(viewRunData.status)[0] }}</el-tag>
          <el-tag size="small" effect="plain">{{ triggerMeta(viewRunData.triggerType)[0] }}触发</el-tag>
          <el-tag v-if="viewRunData.aiMode" size="small" :type="viewRunData.aiMode === 'LLM' ? 'primary' : 'info'">
            {{ viewRunData.aiMode === 'LLM' ? 'LLM 大模型' : '规则引擎' }}
          </el-tag>
          <span class="muted small">{{ viewRunData.startedAt }} · {{ fmtDur(viewRunData.durationMs) }}</span>
        </div>
        <RunSteps :steps="viewRunData.steps" />
        <div v-if="viewRunData.output" class="run-output">
          <div class="run-output-h">执行结果</div>
          <pre>{{ viewRunData.output }}</pre>
        </div>
        <div v-if="viewRunData.error" class="run-err">错误：{{ viewRunData.error }}</div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Cpu, Lightning, ArrowRight, Connection } from '@element-plus/icons-vue'
import RoleAvatar from '../../../components/RoleAvatar.vue'
import RunSteps from '../../../components/RunSteps.vue'
import {
  empBoard, empCreate, empUpdate, empDelete, empSetSkills, empSkills, empSetStatus,
  skillList, taskList, taskCreate, taskUpdate, taskDelete, taskRun, runPage, runDetail,
  condMetrics, wfBoard, wfRun,
  ROLE_CODES, AVATARS,
  roleLabel, categoryMeta, dataSourceLabel, triggerMeta, runStatusMeta
} from '../../../api/digital'
import { modelList, kbPage } from '../../../api/ai'
import { useRouter } from 'vue-router'

const router = useRouter()
const goWorkflow = () => router.push('/digital/workflow')
const wfLastText = v => ({ RUNNING: '运行中', PAUSED: '待审批', DONE: '已完成', FAIL: '失败', REJECTED: '已驳回' }[v] || '未运行')
const loading = ref(false)
const saving = ref(false)
const board = ref([])
const allSkills = ref([])
const models = ref([])
const hasModel = computed(() => models.value.some(m => m.status === 'ACTIVE'))

// ================= 数据加载 =================
const loadBoard = async () => {
  loading.value = true
  try {
    board.value = (await empBoard()) || []
  } finally {
    loading.value = false
  }
}
const loadSkills = async () => { allSkills.value = (await skillList()) || [] }
const loadModels = async () => { models.value = (await modelList()) || [] }

// ================= 知识库绑定（员工级） =================
const kbDocs = ref([])
const loadKbDocs = async () => {
  try {
    const r = await kbPage({ current: 1, size: 100 })
    kbDocs.value = r?.records || r?.list || r?.data || (Array.isArray(r) ? r : [])
  } catch (e) { kbDocs.value = [] }
}
const schedCount = b => (b.tasks || []).filter(t => t.triggerType === 'SCHEDULED').length
const kbCount = ids => String(ids || '').split(',').filter(x => x && x.trim()).length

// ================= 工作流编排任务（团队页展示） =================
const workflows = ref([])
const wfLoading = ref(false)
const loadWorkflows = async () => {
  wfLoading.value = true
  try { workflows.value = (await wfBoard()) || [] } finally { wfLoading.value = false }
}
const wfRunTag = s => s === 'DONE' ? 'success' : s === 'RUNNING' ? 'primary' : s === 'PAUSED' ? 'warning' : s === 'REJECTED' ? 'info' : 'danger'
const wfRunText = s => ({ DONE: '最近：已完成', RUNNING: '最近：执行中', PAUSED: '最近：待审批', FAIL: '最近：失败', REJECTED: '最近：已驳回' }[s] || '最近：未运行')
const onWfRun = async (wf) => {
  try {
    const runId = await wfRun(wf.workflow.id)
    ElMessage.success(`已触发「${wf.workflow.name}」执行，runId=${runId}`)
    setTimeout(loadWorkflows, 2000)
  } catch (e) {
    ElMessage.warning(e.message || '触发失败')
  }
}

// ================= 员工表单 =================
const formVisible = ref(false)
const emptyForm = () => ({ employee: { id: null, name: '', roleCode: 'CUSTOM', avatar: 'custom', persona: '', modelConfigId: null, status: 'ACTIVE', remark: '' }, skillIds: [], kbDocIdsArr: [] })
const form = reactive(emptyForm())

const onRoleChange = v => {
  const r = ROLE_CODES.find(x => x.value === v)
  if (r && !form.employee.avatar) form.employee.avatar = r.avatar
}

const openForm = (emp, b) => {
  Object.assign(form, emptyForm())
  if (emp) {
    Object.assign(form.employee, {
      id: emp.id, name: emp.name, roleCode: emp.roleCode, avatar: emp.avatar,
      persona: emp.persona, modelConfigId: emp.modelConfigId, status: emp.status, remark: emp.remark
    })
    form.skillIds = (b ? b.skills : []).map(s => s.id)
    form.kbDocIdsArr = emp.kbDocIds ? emp.kbDocIds.split(',').map(x => Number(x.trim())).filter(Boolean) : []
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.employee.name) return ElMessage.warning('请填写名称')
  saving.value = true
  try {
    const kbDocIds = form.kbDocIdsArr.length ? form.kbDocIdsArr.join(',') : ''
    const payload = { employee: { ...form.employee, kbDocIds }, skillIds: form.skillIds }
    if (form.employee.id) {
      await empUpdate(form.employee.id, payload)
    } else {
      await empCreate(payload)
    }
    ElMessage.success('已保存')
    formVisible.value = false
    loadBoard()
  } finally { saving.value = false }
}

const onDelete = async b => {
  await ElMessageBox.confirm(`确认删除数字员工「${b.employee.name}」？其任务定义将一并删除（执行记录保留留痕）。`, '提示', { type: 'warning' })
  await empDelete(b.employee.id)
  ElMessage.success('已删除')
  loadBoard()
}

// ================= 在岗/休假快捷切换 =================
const onToggleStatus = async b => {
  const target = b.employee.status
  try {
    await empSetStatus(b.employee.id, target)
    ElMessage.success(target === 'ACTIVE' ? `「${b.employee.name}」已恢复在岗` : `「${b.employee.name}」已休假（定时/条件任务自动跳过）`)
    loadBoard()
  } catch (e) {
    b.employee.status = target === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' // 回滚
    loadBoard()
  }
}

// ================= 详情抽屉 =================
const drawerVisible = ref(false)
const drawerTab = ref('skills')
const drawerEmp = ref(null)
const drawerSkillIds = ref([])
const drawerSkills = ref([])
const drawerTasks = ref([])
const drawerRuns = ref([])

const openDrawer = async (b, tab) => {
  drawerEmp.value = b
  drawerTab.value = tab
  drawerSkillIds.value = b.skills.map(s => s.id)
  drawerSkills.value = b.skills
  drawerVisible.value = true
  await refreshDrawer()
}
const refreshDrawer = async () => {
  if (!drawerEmp.value) return
  const eid = drawerEmp.value.employee.id
  drawerSkills.value = (await empSkills(eid)) || []
  drawerTasks.value = (await taskList({ employeeId: eid })) || []
  const p = await runPage({ current: 1, size: 10, employeeId: eid })
  drawerRuns.value = p?.records || []
}
const saveDrawerSkills = async () => {
  if (!drawerEmp.value) return
  saving.value = true
  try {
    await empSetSkills(drawerEmp.value.employee.id, drawerSkillIds.value)
    ElMessage.success('已保存')
    await refreshDrawer(); loadBoard()
  } finally { saving.value = false }
}
const uninstallSkill = async s => {
  if (!drawerEmp.value) return
  drawerSkillIds.value = drawerSkillIds.value.filter(id => id !== s.id)
  await empSetSkills(drawerEmp.value.employee.id, drawerSkillIds.value)
  await refreshDrawer(); loadBoard()
}

// ================= 任务表单 =================
const taskFormVisible = ref(false)
const emptyTask = () => ({ id: null, name: '', employeeId: null, skillId: null, triggerType: 'MANUAL', cronExpr: '', condSource: 'pendingAward', condOp: '>=', condValue: 1, cooldownMinutes: 60, remark: '' })
const taskForm = reactive(emptyTask())
const cronPresets = [
  { label: '每天 08:00', expr: '0 0 8 * * *' },
  { label: '每天 09:00', expr: '0 0 9 * * *' },
  { label: '工作日 09:00', expr: '0 0 9 * * 1-5' },
  { label: '每小时整点', expr: '0 0 * * * *' },
  { label: '每 30 分钟', expr: '0 */30 * * * *' }
]
// 条件指标：默认全集（后端按取数域返回子集，随所选技能的取数域过滤）
const DEFAULT_COND_SOURCES = [
  { value: 'pendingAward', label: '待审批定标数' },
  { value: 'openProjects', label: '在办招采项目数' },
  { value: 'deviationCount', label: '定标价偏差条数' },
  { value: 'collusionCount', label: '围串标预警数' },
  { value: 'costDeviationRate', label: '成本偏差率(%)' },
  { value: 'overrunProjects', label: '成本超支项目数' },
  { value: 'lowTurnoverProjects', label: '去化偏低项目数' },
  { value: 'avgTurnoverRate', label: '平均去化率(%)' },
  { value: 'receiptRate', label: '回款率(%)' },
  { value: 'dynamicProfit', label: '动态利润' }
]
const condMetricsMap = ref({})
const loadCondMetrics = async () => {
  if (Object.keys(condMetricsMap.value).length) return
  try { condMetricsMap.value = (await condMetrics()) || {} } catch (e) { /* 失败回退默认全集 */ }
}
const condSkill = computed(() => allSkills.value.find(s => s.id === taskForm.skillId))
const condSourcesList = computed(() => {
  const ds = condSkill.value?.dataSource || 'NONE'
  const list = condMetricsMap.value[ds]
  let base = (list && list.length)
    ? list.map(m => ({ value: m.key, label: m.label }))
    : DEFAULT_COND_SOURCES
  // 编辑旧任务时，其指标不在当前域内也保留可选
  const cur = taskForm.condSource
  if (cur && !base.some(c => c.value === cur)) base = [{ value: cur, label: cur }, ...base]
  return base
})
const condWarn = computed(() => {
  const ds = condSkill.value?.dataSource || 'NONE'
  return ds === 'NONE' ? '提示：该技能为通用技能（不采集业务数据），条件触发将不会命中' : ''
})

const openTaskForm = (t, emp) => {
  loadCondMetrics()
  Object.assign(taskForm, emptyTask())
  taskForm.employeeId = emp ? emp.id : (drawerEmp.value ? drawerEmp.value.employee.id : board.value[0]?.employee?.id)
  if (t) {
    Object.assign(taskForm, {
      id: t.id, name: t.name, employeeId: t.employeeId, skillId: t.skillId,
      triggerType: t.triggerType, cronExpr: t.cronExpr || '', remark: t.remark,
      cooldownMinutes: t.cooldownMinutes || 60
    })
    if (t.conditionExpr) {
      try {
        const c = JSON.parse(t.conditionExpr)
        taskForm.condSource = c.source; taskForm.condOp = c.op; taskForm.condValue = Number(c.value)
      } catch (e) { /* 保持默认 */ }
    }
  }
  taskFormVisible.value = true
}

const saveTask = async () => {
  if (!taskForm.name) return ElMessage.warning('请填写任务名称')
  if (!taskForm.skillId) return ElMessage.warning('请选择技能')
  if (taskForm.triggerType === 'SCHEDULED' && !taskForm.cronExpr) return ElMessage.warning('请填写 CRON 表达式')
  if (taskForm.triggerType === 'CONDITIONAL' && !taskForm.condSource) return ElMessage.warning('请选择条件指标')
  saving.value = true
  try {
    const payload = {
      name: taskForm.name,
      employeeId: taskForm.employeeId,
      skillId: taskForm.skillId,
      triggerType: taskForm.triggerType,
      cronExpr: taskForm.triggerType === 'SCHEDULED' ? taskForm.cronExpr : null,
      conditionExpr: taskForm.triggerType === 'CONDITIONAL'
        ? JSON.stringify({ source: taskForm.condSource, op: taskForm.condOp, value: taskForm.condValue }) : null,
      cooldownMinutes: taskForm.triggerType === 'CONDITIONAL' ? taskForm.cooldownMinutes : null,
      remark: taskForm.remark || null
    }
    if (taskForm.id) await taskUpdate(taskForm.id, payload)
    else await taskCreate(payload)
    ElMessage.success('已保存')
    taskFormVisible.value = false
    if (drawerVisible.value) refreshDrawer()
    loadBoard()
  } finally { saving.value = false }
}

const deleteTask = async t => {
  await ElMessageBox.confirm(`确认删除任务「${t.name}」？`, '提示', { type: 'warning' })
  await taskDelete(t.id)
  ElMessage.success('已删除')
  if (drawerVisible.value) refreshDrawer()
  loadBoard()
}

// ================= 执行（选任务 → 实时过程） =================
const runPickVisible = ref(false)
const runPickEmp = ref(null)
const openRun = b => { runPickEmp.value = b; runPickVisible.value = true }

const runLiveVisible = ref(false)
const runLiveTitle = ref('执行中…')
const runLiveAvatar = ref('custom')
const runLive = ref(null)
const runRunning = ref(true)
let pollTimer = null
let pollRunId = null

const skillName = sid => {
  const all = [...allSkills.value, ...board.value.flatMap(b => b.skills)]
  return all.find(s => s.id === sid)?.name || '技能#' + sid
}

const startRun = async t => {
  runPickVisible.value = false
  const emp = board.value.find(b => b.employee.id === t.employeeId)
  if (!emp) return ElMessage.warning('未找到所属员工')
  const runId = await taskRun(t.id)
  beginPolling(runId, t.name, emp.employee.avatar)
}

const beginPolling = (runId, taskName, avatar) => {
  pollRunId = runId
  runRunning.value = true
  runLive.value = { steps: '[]', status: 'RUNNING' }
  runLiveTitle.value = `执行中：${taskName}`
  runLiveAvatar.value = avatar
  runLiveVisible.value = true
  stopPolling()
  pollTimer = setInterval(pollOnce, 1500)
  pollOnce()
}

const pollOnce = async () => {
  if (!pollRunId) return
  try {
    const r = await runDetail(pollRunId)
    runLive.value = r
    if (r.status !== 'RUNNING') {
      runRunning.value = false
      runLiveTitle.value = `${r.status === 'DONE' ? '已完成' : '执行失败'}：${r.taskName || ''}`
      stopPolling()
    }
  } catch (e) { /* 轮询失败继续 */ }
}
const stopPolling = () => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// ================= 运行过程查看 =================
const viewRunVisible = ref(false)
const viewRunData = ref(null)
const viewRun = async r => {
  viewRunData.value = r
  viewRunVisible.value = true
}

// ================= 小工具 =================
const condText = t => {
  try {
    const c = JSON.parse(t.conditionExpr)
    const pool = [...DEFAULT_COND_SOURCES,
      ...Object.values(condMetricsMap.value).flat().map(m => ({ value: m.key, label: m.label }))]
    const src = pool.find(x => x.value === c.source)
    return `${src ? src.label : c.source} ${c.op} ${c.value} 时触发`
  } catch (e) { return t.conditionExpr }
}
const modelLabel = m => {
  if (!m || m.label) return m?.label || '默认'
  if (m.modelName) return `${m.provider} · ${m.modelName}${m.active === false ? '（停用）' : ''}`
  return m.default ? '系统默认' : '默认'
}
const lastRunText = b => {
  if (!b.lastRun) return '—'
  const s = String(b.lastRun.startedAt || '')
  return s ? s.replace('T', ' ').slice(5, 16) : '—'
}
const fmtDur = ms => ms == null ? '—' : (ms >= 1000 ? (ms / 1000).toFixed(1) + 's' : ms + 'ms')
const goRunCenter = () => { runLiveVisible.value = false; router.push('/digital/run') }

onMounted(async () => {
  await Promise.all([loadBoard(), loadSkills(), loadModels(), loadWorkflows(), loadKbDocs()])
})
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.dig-header {
  display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 10px;
  padding: 6px 4px 14px;
}
.dig-header h2 { margin: 0; font-size: 19px; color: #0c2d4e; letter-spacing: 0.5px; }
.dig-sub { margin: 6px 0 0; font-size: 12.5px; color: #7b8a9c; }
.dig-header-actions { display: flex; align-items: center; gap: 10px; }

.dig-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(430px, 1fr)); gap: 16px;
}

.emp-card {
  position: relative; background: #fff; border-radius: 14px; overflow: hidden;
  border: 1px solid #e3ecf5; box-shadow: 0 2px 10px rgba(12, 45, 78, 0.05);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.emp-card:hover { transform: translateY(-3px); box-shadow: 0 10px 26px rgba(12, 45, 78, 0.12); }
.emp-card::before {
  content: ''; position: absolute; inset: 0 0 auto 0; height: 64px;
  background: var(--emp-grad, linear-gradient(120deg, #eef5fb, #f7fafc));
  pointer-events: none;
}
.emp-proc { --emp-grad: linear-gradient(120deg, #dcebfa 0%, #fff 70%); --emp-accent: #185fa5; }
.emp-cost { --emp-grad: linear-gradient(120deg, #d9f2e6 0%, #fff 70%); --emp-accent: #0f8a5f; }
.emp-fin  { --emp-grad: linear-gradient(120deg, #e6e0fb 0%, #fff 70%); --emp-accent: #6b4fd8; }
.emp-sal  { --emp-grad: linear-gradient(120deg, #ffe6d8 0%, #fff 70%); --emp-accent: #e8622c; }
.emp-legal { --emp-grad: linear-gradient(120deg, #faf0d3 0%, #fff 70%); --emp-accent: #b8860b; }
.emp-proj { --emp-grad: linear-gradient(120deg, #dfeaf3 0%, #fff 70%); --emp-accent: #5a7d9a; }
.emp-custom { --emp-grad: linear-gradient(120deg, #d8f1f4 0%, #fff 70%); --emp-accent: #0e9aa7; }

.emp-head { display: flex; gap: 14px; padding: 16px 18px 10px; position: relative; }
.emp-avatar-wrap { position: relative; flex: none; }
.emp-status {
  position: absolute; bottom: 2px; left: 50%; transform: translateX(-50%);
  display: inline-flex; align-items: center; gap: 5px; white-space: nowrap;
  font-size: 11px; padding: 2px 9px; border-radius: 999px; font-weight: 600;
  background: #fff; border: 1px solid #dce6f0;
}
.st-dot { width: 7px; height: 7px; border-radius: 50%; background: #b8c4d0; }
.st-idle .st-dot { background: #2fa36b; }
.st-off { color: #9aa7b5; }
.st-work { color: var(--emp-accent, #185fa5); border-color: var(--emp-accent, #185fa5); }
.st-work .st-dot { background: var(--emp-accent, #185fa5); animation: st-pulse 1.1s ease-in-out infinite; }
@keyframes st-pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(24, 95, 165, 0.45); } 50% { box-shadow: 0 0 0 5px rgba(24, 95, 165, 0); } }

.emp-id { min-width: 0; flex: 1; }
.emp-name-row { display: flex; align-items: center; gap: 8px; }
.emp-name { font-size: 16.5px; font-weight: 700; color: #12293f; }
.emp-role-tag { border-color: var(--emp-accent); color: var(--emp-accent); }
.emp-model {
  display: inline-flex; align-items: center; gap: 4px; margin-top: 6px;
  font-size: 12px; color: #5c6b7c;
}
.emp-persona {
  margin-top: 8px; font-size: 12.5px; color: #7b8a9c; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}

.emp-skills { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 4px 18px 10px; position: relative; }
.skill-chip {
  font-size: 12px; padding: 3px 10px; border-radius: 999px;
  background: #f0f5fa; color: #33526f; border: 1px solid #e0eaf3;
}
.skill-chip.none { color: #b0bdc9; background: #fafbfc; border-style: dashed; }

.emp-stats {
  display: grid; grid-template-columns: repeat(4, 1fr);
  border-top: 1px dashed #e3ecf5; margin: 0 18px; padding: 10px 0;
}
.emp-stats div { text-align: center; }
.emp-stats b { display: block; font-size: 15px; color: var(--emp-accent, #185fa5); }
.emp-stats b.tiny { font-size: 12px; font-family: "SF Mono", Consolas, monospace; }
.emp-stats i { font-style: normal; font-size: 11.5px; color: #9aa7b5; }

.emp-actions { display: flex; align-items: center; gap: 6px; padding: 12px 18px 16px; position: relative; }

/* 表单 */
.avatar-pick { display: flex; gap: 10px; flex-wrap: wrap; }
.avatar-opt {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 8px 10px 6px; border-radius: 10px; cursor: pointer;
  border: 1.5px solid #e3ecf5; transition: all 0.15s;
}
.avatar-opt span { font-size: 11px; color: #7b8a9c; }
.avatar-opt:hover { border-color: #8aadd2; }
.avatar-opt.on { border-color: #185fa5; background: #eef5fb; }
.avatar-opt.on span { color: #185fa5; font-weight: 600; }
.opt-cat { margin-left: 8px; font-size: 11px; color: #9aa7b5; }

/* 执行选择 */
.run-pick-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.run-pick-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 11px 14px; border: 1px solid #e3ecf5; border-radius: 10px; margin-bottom: 8px;
  cursor: pointer; transition: all 0.15s;
}
.run-pick-item:hover { border-color: #185fa5; background: #eef5fb; }
.run-pick-item .arrow { color: #185fa5; }

/* 实时执行 */
.run-live { display: flex; gap: 18px; }
.run-live-side {
  flex: none; width: 140px; display: flex; flex-direction: column; align-items: center; gap: 10px;
  background: #f7fafd; border-radius: 12px; padding: 16px 8px;
}
.run-live-mode { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.run-live-body { flex: 1; min-width: 0; }

.run-output {
  margin-top: 14px; border: 1px solid #e0eaf3; border-radius: 10px; overflow: hidden;
  background: #fbfdff;
}
.run-output-h {
  padding: 8px 12px; font-size: 12.5px; font-weight: 600; color: #0c447c;
  background: #eef5fb; border-bottom: 1px solid #e0eaf3;
}
.run-output pre {
  margin: 0; padding: 12px 14px; font-size: 12.5px; line-height: 1.75;
  white-space: pre-wrap; word-break: break-all; color: #33445a;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  max-height: 320px; overflow: auto;
}
.run-thinking {
  display: flex; align-items: center; gap: 8px; margin-top: 12px;
  font-size: 12.5px; color: #185fa5; padding: 8px 12px;
  background: #eef5fb; border-radius: 8px;
}
.rs-spin-inline {
  width: 12px; height: 12px; border-radius: 50%; flex: none;
  border: 2px solid #b9cddd; border-top-color: #185fa5;
  animation: rt-rot 0.8s linear infinite;
}
@keyframes rt-rot { to { transform: rotate(360deg); } }
.run-live-foot { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
.run-err { margin-top: 10px; color: #c45656; font-size: 12.5px; }
.view-run-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }

/* 抽屉 */
.drawer-tip { margin-bottom: 4px; }
.skill-row {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border: 1px solid #e8eff6; border-radius: 10px; margin-bottom: 8px;
}
.skill-row-icon {
  flex: none; width: 34px; height: 34px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; font-weight: 700; color: #fff; background: #8aadd2;
}
.skill-row-icon.cat-BUILTIN { background: #185fa5; }
.skill-row-icon.cat-MARKET { background: #2fa36b; }
.skill-row-icon.cat-CUSTOM { background: #e8a33d; }
.skill-row-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }

.task-row {
  display: flex; justify-content: space-between; align-items: flex-start; gap: 10px;
  padding: 11px 12px; border: 1px solid #e8eff6; border-radius: 10px; margin-bottom: 8px;
}
.task-row-actions { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.cron-presets { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.cond-warn { margin-top: 6px; font-size: 12px; color: #b76e00; background: #fdf4e3; border: 1px solid #ecd39c; border-radius: 6px; padding: 5px 8px; }
.emp-toggle { margin: 0 4px; }

/* 参与的工作流 */
.emp-wfs {
  display: flex; flex-wrap: wrap; gap: 6px;
  padding: 8px 12px; margin: 0 12px;
  background: #f5f9fd; border: 1px dashed #cfe0ef; border-radius: 10px;
}
.wf-chip {
  display: inline-flex; align-items: center; gap: 4px; cursor: pointer;
  font-size: 12px; color: #33506e; background: #fff;
  border: 1px solid #d8e6f2; border-radius: 999px; padding: 3px 10px;
  transition: all .15s;
}
.wf-chip:hover { border-color: #185fa5; color: #185fa5; box-shadow: 0 1px 4px rgba(24,95,165,.15); }
.wf-chip .el-icon { font-size: 12px; }
.wf-need-approve {
  font-style: normal; font-size: 10.5px; color: #b76e00; background: #fdf4e3;
  border: 1px solid #ecd39c; border-radius: 4px; padding: 0 4px;
}
.wf-last { font-style: normal; font-size: 10.5px; margin-left: 2px; }
.wf-last.st-DONE { color: #2fa36b; }
.wf-last.st-RUNNING { color: #185fa5; }
.wf-last.st-PAUSED { color: #b76e00; }
.wf-last.st-FAIL, .wf-last.st-REJECTED { color: #e05656; }
.wf-last.st-none { color: #9aa7b5; }

/* 工作流编排任务区块 */
.wfsec { margin-top: 26px; }
.wfsec-head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 12px; }
.wfsec-title { margin: 0; font-size: 17px; color: #0c2d4e; }
.wfsec-sub { margin: 4px 0 0; font-size: 12.5px; color: #6b7280; }
.wfsec-grid { display: flex; flex-direction: column; gap: 12px; }
.wbwf-card {
  background: #fff; border: 1px solid #e8edf3; border-radius: 12px; padding: 14px 16px;
  transition: box-shadow .15s;
}
.wbwf-card:hover { box-shadow: 0 4px 14px rgba(12,72,124,.08); }
.wbwf-top { display: flex; align-items: center; gap: 8px; font-size: 14.5px; }
.wbwf-desc { margin: 5px 0 8px; font-size: 12.5px; color: #6b7280; }
.wbwf-chain { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 10px; }
.wbwf-chip {
  display: inline-flex; align-items: center; gap: 3px; font-size: 12px; color: #33506e;
  background: #f2f7fc; border: 1px solid #d8e6f2; border-radius: 6px; padding: 3px 9px;
}
.wbwf-relay { font-style: normal; color: #185fa5; font-size: 11px; }
.wbwf-sep { color: #9aa7b5; font-size: 13px; }
.wbwf-none { font-size: 12px; color: #e05656; }
.wbwf-foot { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.wbwf-actions { margin-left: auto; display: inline-flex; gap: 6px; }
.wbwf-empty {
  text-align: center; padding: 26px 16px; background: #fff; border: 1px dashed #cfe0ef; border-radius: 12px;
  color: #6b7280; font-size: 13px; display: flex; flex-direction: column; gap: 10px; align-items: center;
}
.emp-extra-chips { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.extra-chip {
  font-size: 11.5px; padding: 2px 10px; border-radius: 999px; border: 1px solid; cursor: default;
}
.extra-chip.sched { background: #eef4fd; color: #185fa5; border-color: #cddcf0; }
.extra-chip.kb { background: #eefaf3; color: #1d7a4c; border-color: #c4e6d2; }
.cron-preset { cursor: pointer; user-select: none; }
.cron-preset:hover { color: #185fa5; border-color: #185fa5; }
.cond-line { display: flex; gap: 8px; }

.run-row {
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
  padding: 10px 12px; border: 1px solid #e8eff6; border-radius: 10px; margin-bottom: 8px;
}
.muted { color: #909399; }
.small { font-size: 12px; }
</style>
