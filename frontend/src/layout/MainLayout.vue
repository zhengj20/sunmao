<template>
  <div class="layout">
    <!-- 顶部一级导航（13 个业务模块） -->
    <header class="topnav">
      <div class="brand">
        <span class="logo-mark">榫</span>
        <span class="brand-text">榫卯 SunMao</span>
      </div>
      <nav class="topnav-tabs">
        <div
          v-for="m in store.menus"
          :key="m.id"
          class="topnav-tab"
          :class="{ active: isActive(m) }"
          @click="onTopClick(m)"
        >
          <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
          <span>{{ m.name }}</span>
        </div>
      </nav>
      <div class="topnav-right">
        <el-tag size="small" effect="plain" type="info" class="date-tag">{{ today }}</el-tag>
        <el-dropdown @command="onCommand">
          <span class="user-chip">
            <el-avatar :size="26" style="background:#185fa5">{{ nameChar }}</el-avatar>
            <span class="uname">{{ store.userInfo?.realName || store.userInfo?.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 移动端：二级功能抽屉 -->
    <div v-if="isMobile && mobileOpen" class="drawer-mask" @click="mobileOpen = false"></div>
    <aside v-if="isMobile" class="m-drawer" :class="{ open: mobileOpen }">
      <div class="m-drawer-title">{{ activeTop?.name }} · 功能</div>
      <el-menu v-if="activeTop?.children?.length" :default-active="route.path" router @select="mobileOpen = false">
        <el-menu-item v-for="c in activeTop.children" :key="c.id" :index="c.path">{{ c.name }}</el-menu-item>
      </el-menu>
      <div v-else class="m-empty">该模块为单页，已直接进入</div>
    </aside>

    <!-- 主体：桌面左侧二级菜单 + 内容区 -->
    <div class="body">
      <aside v-if="!isMobile && activeTop?.children?.length" class="sub-sidebar">
        <div class="sub-title">{{ activeTop.name }} · 功能菜单</div>
        <!-- 分组菜单（有配置时） -->
        <nav v-if="sidebarGroups" class="gmenu">
          <div v-for="g in sidebarGroups" :key="g.label" class="ggroup">
            <div class="glabel">{{ g.label }}</div>
            <a
              v-for="it in g.items" :key="it.path"
              class="gitem" :class="{ active: route.path === it.path }"
              @click="router.push(it.path)"
            >
              <el-icon v-if="it.icon" class="gicon"><component :is="it.icon" /></el-icon>
              <span class="gtext">{{ it.name }}</span>
            </a>
          </div>
        </nav>
        <!-- 平铺菜单（无分组配置时） -->
        <el-menu v-else :default-active="route.path" router>
          <el-menu-item v-for="c in activeTop.children" :key="c.id" :index="c.path">{{ c.name }}</el-menu-item>
        </el-menu>
      </aside>
      <main class="main">
        <router-view v-if="routeLoaded" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { getSidebarGroups } from './sidebarGroups'
import dayjs from 'dayjs'

const store = useUserStore()
const route = useRoute()
const router = useRouter()
const today = dayjs().format('YYYY年MM月DD日')
const isMobile = ref(false)
const mobileOpen = ref(false)

const routeLoaded = computed(() => !!route.matched.length)

/** 当前激活的一级模块：按当前路由匹配（子页或模块自身页），否则默认第一个（工作台） */
const activeTop = computed(() => {
  return store.menus.find(m =>
    (m.children && m.children.some(c => c.path === route.path)) || m.path === route.path
  ) || store.menus[0] || null
})

const isActive = m => activeTop.value?.id === m.id

/** 当前模块的分组菜单（有分组配置时返回数组，否则 null 走平铺菜单） */
const sidebarGroups = computed(() =>
  getSidebarGroups(activeTop.value?.name, activeTop.value?.children || [])
)

/** 点击顶部模块：进入该模块首个功能页；移动端有二级则打开抽屉 */
const onTopClick = m => {
  const first = (m.children && m.children.length) ? m.children[0].path : (m.path || null)
  if (first && first !== route.path) router.push(first)
  if (isMobile.value && m.children && m.children.length) mobileOpen.value = true
}

const nameChar = computed(() => (store.userInfo?.realName || 'U').charAt(0))

const onCommand = cmd => {
  if (cmd === 'logout') {
    store.logout()
    router.push('/login')
  }
}

const updateMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) mobileOpen.value = false
}

onMounted(() => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

<style scoped>
.layout { height: 100%; display: flex; flex-direction: column; }

/* ===== 顶部一级导航 ===== */
.topnav {
  height: 64px; flex-shrink: 0;
  background: linear-gradient(135deg, #0a2540 0%, #0c2d4e 60%, #103a63 100%);
  border-bottom: 2px solid var(--reip-sidebar-active, #185fa5);
  display: flex; align-items: center; gap: 14px; padding: 0 18px;
}
.brand { display: flex; align-items: center; gap: 9px; white-space: nowrap; }
.logo-mark {
  width: 30px; height: 30px; border-radius: 7px; flex-shrink: 0;
  background: var(--reip-sidebar-active, #185fa5); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 13px;
}
.brand-text { color: #fff; font-weight: 700; font-size: 15px; letter-spacing: .5px; }
.topnav-tabs {
  flex: 1; min-width: 0; display: flex; align-items: center; gap: 2px;
  overflow-x: auto; scrollbar-width: thin; scrollbar-color: rgba(255,255,255,.25) transparent;
}
.topnav-tabs::-webkit-scrollbar { height: 6px; }
.topnav-tabs::-webkit-scrollbar-thumb { background: rgba(255,255,255,.2); border-radius: 3px; }
.topnav-tab {
  display: inline-flex; align-items: center; gap: 6px; flex-shrink: 0;
  padding: 8px 14px; border-radius: 8px; cursor: pointer; user-select: none;
  color: #a8c0d8; font-size: 14px; white-space: nowrap;
  transition: background .15s, color .15s;
}
.topnav-tab:hover { background: rgba(255,255,255,.08); color: #fff; }
.topnav-tab.active { background: var(--reip-sidebar-active, #185fa5); color: #fff; font-weight: 600; }
.topnav-right { display: flex; align-items: center; gap: 12px; white-space: nowrap; }
.user-chip { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.uname { font-size: 13px; color: #e6eef6; }

/* ===== 主体 ===== */
.body { flex: 1; min-height: 0; display: flex; }
.sub-sidebar {
  width: 176px; flex-shrink: 0;
  background: linear-gradient(180deg, #f6f9fd 0%, #edf3fa 100%);
  border-right: 1px solid #dbe6f1; overflow-y: auto;
}
.sub-title { padding: 12px 14px 6px; font-size: 11px; font-weight: 600; color: #8aa2bd; letter-spacing: 1px; }
.sub-sidebar :deep(.el-menu) {
  border-right: none; background-color: transparent;
  --el-menu-item-height: 40px; padding: 0 8px 8px;
}
.sub-sidebar :deep(.el-menu-item) {
  height: 40px; line-height: 40px;
  padding: 0 12px; margin-bottom: 2px; border-radius: 8px;
  background-color: transparent; font-size: 13.5px; color: #46586e;
  transition: background-color .15s ease, color .15s ease;
}
.sub-sidebar :deep(.el-menu-item:hover) { background-color: #e5eef7; color: #0f4c8a; }
.sub-sidebar :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #d9e8f7 0%, #e9f2fb 100%);
  color: #0f4c8a; font-weight: 600;
}
.sub-sidebar :deep(.el-menu-item.is-active::before) {
  content: ''; position: absolute; left: 3px; top: 10px; bottom: 10px; width: 3px;
  background: var(--reip-sidebar-active, #185fa5); border-radius: 2px;
}
/* ===== 分组菜单（参考样式：分组标题 + 图标 + 圆角高亮） ===== */
.gmenu { padding: 2px 10px 12px; }
.ggroup { margin-top: 4px; }
.ggroup + .ggroup { margin-top: 10px; }
.glabel {
  padding: 6px 8px 6px; font-size: 11px; font-weight: 600; letter-spacing: 1px;
  color: #93a6bd;
}
.gitem {
  position: relative; display: flex; align-items: center; gap: 10px;
  height: 38px; padding: 0 10px; margin-bottom: 2px; border-radius: 9px;
  color: #46586e; font-size: 13.5px; cursor: pointer; text-decoration: none;
  transition: background-color .15s ease, color .15s ease;
}
.gitem .gicon { font-size: 17px; color: #6b829c; flex-shrink: 0; transition: color .15s ease; }
.gitem .gtext { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gitem:hover { background-color: #e5eef7; color: #0f4c8a; }
.gitem:hover .gicon { color: #185fa5; }
.gitem.active {
  background: linear-gradient(90deg, #d9e8f7 0%, #e9f2fb 100%);
  color: #0f4c8a; font-weight: 600;
}
.gitem.active .gicon { color: #185fa5; }
.gitem.active::before {
  content: ''; position: absolute; left: 2px; top: 9px; bottom: 9px; width: 3px;
  background: var(--reip-sidebar-active, #185fa5); border-radius: 2px;
}
.main { flex: 1; min-width: 0; background: #f5f7fa; overflow: auto; }

/* ===== 移动端二级抽屉 ===== */
.m-drawer {
  position: fixed; left: 0; top: 64px; bottom: 0; width: 260px; z-index: 201;
  background: #fff; box-shadow: 2px 0 12px rgba(0,0,0,.2);
  transform: translateX(-100%); transition: transform .25s ease; overflow-y: auto;
}
.m-drawer.open { transform: translateX(0); }
.m-drawer-title { padding: 14px 18px 6px; font-size: 13px; font-weight: 600; color: #475569; border-bottom: 1px solid #f1f5f9; }
.m-drawer :deep(.el-menu) { border-right: none; --el-menu-item-height: 40px; }
.m-empty { padding: 24px 18px; color: #94a3b8; font-size: 13px; }
.drawer-mask { position: fixed; inset: 0; background: rgba(0,0,0,.4); z-index: 200; }

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .brand-text { display: none; }
  .topnav { padding: 0 10px; gap: 8px; }
  .topnav-tab { padding: 7px 10px; font-size: 13px; }
  .date-tag, .uname { display: none; }
  .sub-sidebar { display: none; }
}
</style>
