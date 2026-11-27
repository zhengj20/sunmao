<template>
  <div class="page-container workbench">
    <!-- ===== 命令条 ===== -->
    <section class="hero rise">
      <div class="hero-left">
        <div class="hero-avatar">{{ (userInfo?.realName || 'U').charAt(0) }}</div>
        <div class="hero-text">
          <div class="hero-hi">{{ greetText }}，{{ userInfo?.realName || userInfo?.username || '' }}</div>
          <div class="hero-sub">
            <span>{{ today }}</span>
            <span class="sep" v-if="todoCount > 0">·</span>
            <template v-if="todoCount > 0">
              <span>您有 {{ todoCount }} 条待办审批</span>
              <span class="sep">·</span>
            </template>
            <span v-if="timeoutCount > 0" class="hero-warn">{{ timeoutCount }} 条已超时，请优先处理</span>
            <span v-else class="hero-ok">无超时任务，工作顺利</span>
          </div>
        </div>
      </div>
      <div class="hero-actions">
        <el-button type="primary" class="hero-btn" @click="go('/workflow/todo')">
          去处理待办<el-icon style="margin-left:5px"><ArrowRight /></el-icon>
        </el-button>
        <el-button class="hero-btn-ghost" @click="go('/ai/chat')">AI 助手</el-button>
      </div>
    </section>

    <!-- ===== 模块快捷入口（顶部一级模块，可快速进入） ===== -->
    <section class="quickbar rise d1">
      <div v-for="q in quickModules" :key="q.id" class="quick-item" @click="go(q.path)">
        <span class="q-ico">
          <el-icon v-if="q.icon" :size="18"><component :is="q.icon" /></el-icon>
        </span>
        <span class="q-label">{{ q.name }}</span>
      </div>
    </section>

    <!-- ===== 经营总览 + 我的工作区 ===== -->
    <el-row :gutter="12" class="row row-eq">
      <el-col :span="16">
        <el-card shadow="never" class="biz-card rise d2">
          <template #header>
            <div class="card-head">
              <div>
                <span class="card-title">经营总览</span>
                <span class="card-sub">集团口径 · 实时</span>
              </div>
              <el-button link type="primary" @click="go('/cockpit')">进入驾驶舱<el-icon style="margin-left:3px"><ArrowRight /></el-icon></el-button>
            </div>
          </template>
          <div class="metric-grid">
            <div class="metric">
              <div class="m-label">在建项目</div>
              <div class="m-value num">{{ stats.projectCount ?? 0 }}<span class="m-unit">个</span></div>
            </div>
            <div class="metric">
              <div class="m-label">合同签约额</div>
              <div class="m-value num">{{ wan(stats.contractAmount) }}<span class="m-unit">万</span></div>
            </div>
            <div class="metric">
              <div class="m-label">累计实付</div>
              <div class="m-value num">{{ wan(stats.paymentAmount) }}<span class="m-unit">万</span></div>
            </div>
            <div class="metric">
              <div class="m-label">动态成本</div>
              <div class="m-value num">{{ wan(stats.dynamicCost) }}<span class="m-unit">万</span></div>
            </div>
            <div class="metric accent-green">
              <div class="m-label">动态利润</div>
              <div class="m-value num">{{ wan(stats.dynamicProfit) }}<span class="m-unit">万</span></div>
            </div>
            <div class="metric">
              <div class="m-label">当前货值</div>
              <div class="m-value num">{{ wan(stats.saleValue) }}<span class="m-unit">万</span></div>
            </div>
          </div>

          <div class="health">
            <div class="health-head">
              <span class="health-title">项目成本健康</span>
              <div class="health-legend">
                <span><i class="dot red" />预警</span>
                <span><i class="dot yellow" />关注</span>
                <span><i class="dot green" />正常</span>
              </div>
            </div>
            <div v-if="healthSorted.length" class="health-list">
              <div
                v-for="p in healthSorted" :key="p.name"
                class="health-item" :class="'lv-' + (p.light || '').toLowerCase()"
                @click="go('/cockpit')"
              >
                <i class="dot" :class="(p.light || '').toLowerCase()" />
                <span class="h-name">{{ p.name }}</span>
                <span class="h-dev num" :class="p.costDeviation > 0 ? 'up' : 'down'">
                  {{ p.costDeviation == null ? '—' : (p.costDeviation > 0 ? '+' : '') + devWan(p.costDeviation) }}
                </span>
              </div>
            </div>
            <div v-else class="health-empty">暂无项目健康数据</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" class="mine-card rise d2">
          <template #header><span class="card-title">我的工作区</span></template>
          <div class="todo-hero">
            <div class="th-num num">{{ todoCount }}</div>
            <div class="th-meta">
              <div class="th-label">待办审批</div>
              <div class="th-badges">
                <span class="badge" :class="{ warn: timeoutCount > 0 }">超时 {{ timeoutCount }}</span>
                <span class="badge">在途 {{ inst.inflightCount ?? 0 }}</span>
                <span class="badge">本月完成 {{ inst.monthFinishedCount ?? 0 }}</span>
              </div>
            </div>
          </div>
          <el-button type="primary" class="mine-cta" @click="go('/workflow/todo')">
            前往审批中心<el-icon style="margin-left:4px"><ArrowRight /></el-icon>
          </el-button>
          <el-divider class="mine-div" />
          <div class="draft-zone">
            <div class="draft-head">我的草稿</div>
            <div v-if="myDrafts.length" class="draft-list">
              <div v-for="d in myDrafts" :key="d.bizType + d.bizId" class="draft-item">
                <el-tag size="small" effect="plain" class="d-tag">{{ d.bizType }}</el-tag>
                <span class="d-title">{{ d.title }}</span>
                <span class="d-time">{{ fmtDate(d.createdAt) }}</span>
              </div>
            </div>
            <el-empty v-else description="暂无草稿单据" :image-size="48" class="draft-empty" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 动态 / 公告 / 数字员工 ===== -->
    <el-row :gutter="12" class="row row-eq">
      <el-col :span="8">
        <el-card shadow="never" class="fill-card rise d3">
          <template #header><span class="card-title">最新动态</span></template>
          <div v-if="recentEvents.length" class="feed">
            <div v-for="(e, i) in recentEvents" :key="i" class="feed-item">
              <span class="f-dot" />
              <div class="f-body">
                <span class="f-topic">{{ e.topic }}</span>
                <span class="f-time">{{ fmtTime(e.createdAt) }}</span>
              </div>
            </div>
          </div>
          <div v-else class="feed-empty">暂无系统动态</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" class="fill-card rise d3">
          <template #header><span class="card-title">集团公告</span></template>
          <div v-if="notices.length" class="notice-list">
            <div v-for="n in notices" :key="n.id" class="notice-item" @click="showNotice(n)">
              <el-icon class="n-ico" color="#185fa5"><Bell /></el-icon>
              <span class="n-title">{{ n.title }}</span>
              <span class="n-time">{{ (n.publishTime || '').slice(5, 10) }}</span>
            </div>
          </div>
          <div v-else class="feed-empty">暂无公告</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" class="fill-card rise d3">
          <template #header>
            <div class="card-head">
              <span class="card-title">数字员工团队</span>
              <el-button link type="primary" @click="go('/digital/employee')">进入团队</el-button>
            </div>
          </template>
          <div v-if="digitalEmps.length" class="emp-grid">
            <div v-for="emp in digitalEmps" :key="emp.id" class="emp-item" @click="go('/digital/employee')">
              <span class="e-avatar" :style="{ background: avatarColor(emp.avatar) }">{{ (emp.name || '?').charAt(0) }}</span>
              <div class="e-info">
                <div class="e-name">{{ emp.name }}</div>
                <div class="e-role">{{ roleLabel(emp.roleCode) }}</div>
              </div>
              <i class="e-dot" :class="emp.status === 'ACTIVE' ? 'on' : 'off'" />
            </div>
          </div>
          <div v-else class="feed-empty">暂无数字员工</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 公告详情 -->
    <el-dialog v-model="noticeDlg" :title="notice?.title" width="560px">
      <div class="notice-time">{{ notice?.publishTime }}</div>
      <div class="notice-content">{{ notice?.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../../../store/user'
import { workbench } from '../../../api/portal'
import { cockpitOverview } from '../../../api/cockpit'
import { instanceStats } from '../../../api/workflow'
import { empList, roleLabel } from '../../../api/digital'
import { fmtDate, fmtTime, wan } from '../../../utils/format'
import dayjs from 'dayjs'
import { ArrowRight, Bell } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const userInfo = computed(() => store.userInfo)
const go = p => router.push(p)

const weekChar = '日一二三四五六'.charAt(dayjs().day())
const today = `${dayjs().format('YYYY年MM月DD日')} 周${weekChar}`
const hour = dayjs().hour()
const greetText = hour < 6 ? '凌晨好' : hour < 9 ? '早上好' : hour < 12 ? '上午好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'

// ===== 数据源 =====
const wb = ref({})
const stats = computed(() => wb.value.stats || {})
const myDrafts = computed(() => wb.value.myDrafts || [])
const recentEvents = computed(() => wb.value.recentEvents || [])
const notices = computed(() => wb.value.notices || [])
const todoCount = computed(() => Number(wb.value.todoCount || 0))

const projects = ref([])
const healthSorted = computed(() => {
  const order = { RED: 0, YELLOW: 1, GREEN: 2 }
  return [...projects.value].sort((a, b) => (order[a.light] ?? 3) - (order[b.light] ?? 3))
})

const inst = ref({})
const timeoutCount = computed(() => Number(inst.value.timeoutCount || 0))

const digitalEmps = ref([])
const avatarColor = theme => ({ proc: '#185fa5', cost: '#0f8a5f', fin: '#6b4fd8', sal: '#e8622c', legal: '#b8860b', proj: '#5a7d9a', custom: '#0e9aa7' }[theme] || '#0e9aa7')

const devWan = v => Math.abs(Math.round(Number(v) / 10000)) + '万'

// ===== 模块快捷入口（顶部一级模块，排除当前所在模块，点击直达该模块首页） =====
const quickModules = computed(() => {
  const cur = route.path
  return store.menus
    .filter(m => m.path !== cur && !(m.children || []).some(c => c.path === cur))
    .map(m => ({
      id: m.id,
      name: m.name,
      icon: m.icon || null,
      path: (m.children && m.children.length) ? m.children[0].path : m.path
    }))
})

// ===== 公告 =====
const noticeDlg = ref(false)
const notice = ref(null)
const showNotice = n => { notice.value = n; noticeDlg.value = true }

// ===== 加载（并行 + 单项失败降级） =====
const load = async () => {
  const [w, c, s, e] = await Promise.allSettled([workbench(), cockpitOverview(), instanceStats(), empList()])
  if (w.status === 'fulfilled') wb.value = w.value || {}
  if (c.status === 'fulfilled') projects.value = c.value?.projects || []
  if (s.status === 'fulfilled') inst.value = s.value || {}
  if (e.status === 'fulfilled') digitalEmps.value = e.value || []
}

onMounted(load)
</script>

<style scoped>
/* ===== 入场动画 ===== */
.rise { animation: rise .45s cubic-bezier(.22,.68,.35,1) both; }
.d1 { animation-delay: .06s; } .d2 { animation-delay: .12s; } .d3 { animation-delay: .18s; }
@keyframes rise { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
@media (prefers-reduced-motion: reduce) { .rise, .d1, .d2, .d3 { animation: none; } }

.row { margin-bottom: 12px; }

/* ===== 命令条 ===== */
.hero {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  background: linear-gradient(120deg, #0c2d4e 0%, #185fa5 78%, #2a74bd 100%);
  border-radius: 12px; padding: 20px 24px; margin-bottom: 12px; color: #fff;
  box-shadow: 0 8px 24px -12px rgba(12,45,78,.55);
}
.hero-left { display: flex; align-items: center; gap: 16px; min-width: 0; }
.hero-avatar {
  width: 52px; height: 52px; border-radius: 14px; flex-shrink: 0;
  background: rgba(255,255,255,.16); border: 1px solid rgba(255,255,255,.25);
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 700;
}
.hero-hi { font-size: 20px; font-weight: 700; letter-spacing: .5px; }
.hero-sub { font-size: 12.5px; opacity: .85; margin-top: 7px; display: flex; align-items: center; flex-wrap: wrap; }
.hero-sub .sep { margin: 0 8px; opacity: .5; }
.hero-warn { color: #ffd9a8; font-weight: 600; }
.hero-ok { color: #bfe8c8; }
.hero-actions { display: flex; gap: 10px; flex-shrink: 0; }
.hero-btn { font-weight: 600; }
.hero-btn-ghost {
  background: rgba(255,255,255,.12); border-color: rgba(255,255,255,.3); color: #fff;
}
.hero-btn-ghost:hover { background: rgba(255,255,255,.22); color: #fff; border-color: rgba(255,255,255,.5); }

/* ===== 常用动作条 ===== */
.quickbar {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px;
  background: #fff; border: 1px solid #e8edf3; border-radius: 12px;
  padding: 12px; margin-bottom: 12px;
}
.quick-item {
  display: flex; align-items: center; gap: 9px;
  padding: 9px 12px; border-radius: 9px; cursor: pointer;
  transition: background .15s, transform .1s;
}
.quick-item:hover { background: #f4f8fc; transform: translateY(-1px); }
.q-ico {
  width: 32px; height: 32px; border-radius: 8px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: #eef5fb; color: #185fa5; transition: background .15s;
}
.quick-item:hover .q-ico { background: #dce9f6; }
.q-label { font-size: 13px; font-weight: 500; color: #1f2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* ===== 通用卡片 ===== */
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-sub { font-size: 11.5px; color: #9ca3af; margin-left: 8px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }

/* ===== 经营总览 ===== */
.metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.metric {
  background: #f7fafd; border: 1px solid #edf2f8; border-radius: 10px; padding: 13px 14px;
  position: relative; overflow: hidden;
}
.metric::before {
  content: ''; position: absolute; left: 0; top: 10px; bottom: 10px; width: 3px; border-radius: 2px;
  background: #c3d6e8;
}
.metric.accent-green::before { background: #67c23a; }
.m-label { font-size: 12px; color: #6b7280; margin-bottom: 7px; padding-left: 9px; }
.m-value { font-size: 21px; font-weight: 700; color: #0c2d4e; padding-left: 9px; line-height: 1.15; }
.metric.accent-green .m-value { color: #3f9142; }
.m-unit { font-size: 12px; font-weight: 500; color: #9ca3af; margin-left: 3px; }

/* ===== 项目成本健康 ===== */
.health { margin-top: 14px; border-top: 1px dashed #e5e7eb; padding-top: 12px; }
.health-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.health-title { font-size: 12.5px; font-weight: 700; color: #374151; }
.health-legend { display: flex; gap: 12px; font-size: 11.5px; color: #6b7280; }
.health-legend span { display: inline-flex; align-items: center; gap: 4px; }
.health-list { display: flex; flex-direction: column; gap: 6px; }
.health-item {
  display: flex; align-items: center; gap: 9px;
  padding: 8px 12px; border-radius: 8px; cursor: pointer;
  background: #f7fafd; border: 1px solid #edf2f8;
  transition: background .15s, border-color .15s;
}
.health-item:hover { background: #eef5fb; border-color: #d5e3f0; }
.health-item.lv-red { background: #fdf3f3; border-color: #f3d5d5; }
.health-item.lv-yellow { background: #fdf8ef; border-color: #f0e2c8; }
.h-name { flex: 1; font-size: 13px; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.h-dev { font-size: 12.5px; font-weight: 600; flex-shrink: 0; }
.h-dev.up { color: #e6a23c; }
.h-dev.down { color: #67c23a; }
.health-empty { font-size: 12.5px; color: #9ca3af; padding: 8px 2px; }

.dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.dot.green { background: #67c23a; box-shadow: 0 0 5px rgba(103,194,58,.55); }
.dot.yellow { background: #e6a23c; box-shadow: 0 0 5px rgba(230,162,60,.55); }
.dot.red { background: #f56c6c; box-shadow: 0 0 5px rgba(245,108,108,.6); }

/* ===== 我的工作区 ===== */
.todo-hero { display: flex; align-items: center; gap: 16px; }
.th-num { font-size: 44px; font-weight: 700; color: #185fa5; line-height: 1; }
.th-label { font-size: 12.5px; color: #6b7280; margin-bottom: 8px; }
.th-badges { display: flex; gap: 6px; flex-wrap: wrap; }
.badge {
  font-size: 11.5px; color: #4b5563; background: #f1f5f9;
  padding: 3px 9px; border-radius: 20px;
}
.badge.warn { color: #c2452d; background: #fdecec; font-weight: 600; }
.mine-cta { width: 100%; margin-top: 14px; font-weight: 600; }
.mine-div { margin: 16px 0 12px; }
.draft-head { font-size: 12.5px; font-weight: 700; color: #374151; margin-bottom: 8px; }
.draft-item {
  display: flex; align-items: center; gap: 9px;
  padding: 8px 2px; border-bottom: 1px dashed #eef0f3; font-size: 13px;
}
.draft-item:last-child { border-bottom: none; }
.d-tag { flex-shrink: 0; }
.d-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #1f2937; }
.d-time { font-size: 11.5px; color: #9ca3af; flex-shrink: 0; }
.mine-card :deep(.el-card__body) { display: flex; flex-direction: column; }
.draft-zone { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.draft-empty { flex: 1; margin: 0; justify-content: center; }
.draft-empty :deep(.el-empty__description p) { color: #9ca3af; font-size: 12.5px; }

/* ===== 等高行：卡片填满列，同排卡片高度一致 ===== */
.row-eq :deep(.el-col) { display: flex; flex-direction: column; }
.row-eq :deep(.el-col > .el-card) { flex: 1; display: flex; flex-direction: column; width: 100%; }
.row-eq :deep(.el-col > .el-card > .el-card__body) { flex: 1; }

/* ===== 动态 / 公告 / 数字员工 ===== */
.fill-card :deep(.el-card__body) { min-height: 180px; overflow-y: auto; display: flex; flex-direction: column; }
.feed { display: flex; flex-direction: column; }
.feed-item { display: flex; gap: 10px; padding: 9px 2px; border-bottom: 1px dashed #eef0f3; }
.feed-item:last-child { border-bottom: none; }
.f-dot { width: 7px; height: 7px; border-radius: 50%; background: #185fa5; margin-top: 6px; flex-shrink: 0; }
.f-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.f-topic { font-size: 13px; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.f-time { font-size: 11.5px; color: #9ca3af; }
.feed-empty { flex: 1; display: flex; align-items: center; justify-content: center; font-size: 12.5px; color: #9ca3af; }

.notice-item {
  display: flex; align-items: center; gap: 9px;
  padding: 9px 2px; border-bottom: 1px dashed #eef0f3; font-size: 13px; cursor: pointer;
}
.notice-item:last-child { border-bottom: none; }
.n-ico { flex-shrink: 0; }
.n-title { flex: 1; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-item:hover .n-title { color: #185fa5; }
.n-time { color: #9ca3af; font-size: 11.5px; flex-shrink: 0; }
.notice-time { font-size: 12px; color: #9ca3af; margin-bottom: 12px; }
.notice-content { font-size: 13.5px; color: #374151; line-height: 1.9; }

.emp-grid { display: flex; flex-direction: column; gap: 8px; max-height: 232px; overflow-y: auto; padding-right: 2px; }
.emp-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 10px; border-radius: 9px; cursor: pointer;
  background: #f8fafc; border: 1px solid #eef2f6;
  transition: background .15s, border-color .15s, transform .1s;
}
.emp-item:hover { background: #eef4fb; border-color: #d5e3f0; transform: translateX(2px); }
.e-avatar {
  width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 14px; font-weight: 700;
}
.e-info { flex: 1; min-width: 0; }
.e-name { font-size: 13px; font-weight: 600; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.e-role { font-size: 11px; color: #6b7280; margin-top: 1px; }
.e-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.e-dot.on { background: #67c23a; box-shadow: 0 0 4px rgba(103,194,58,.5); }
.e-dot.off { background: #c0c4cc; }

/* ===== 响应式 ===== */
@media (max-width: 1100px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .hero { flex-direction: column; align-items: flex-start; }
  .quickbar { grid-template-columns: repeat(2, 1fr); }
}
</style>
