import { createRouter, createWebHistory } from 'vue-router'
import { h } from 'vue'
import { useUserStore } from '../store/user'

// 视图组件自动收集：后端菜单 component 字段（如 mdm/project/index）映射到 src/views/**/index.vue
const viewModules = import.meta.glob('../views/**/*.vue')
const compMap = {}
for (const path of Object.keys(viewModules)) {
  const m = path.match(/\.\.\/views\/(.+)\.vue$/)
  if (m) compMap[m[1]] = viewModules[path]
}

// 404 兜底：必须是真实路由（非 redirect）。
// 若用 redirect，会在路由解析阶段（beforeEach 之前）抢先命中，导致动态路由尚未注册时
// 目标路径被吞并跳转到 /workbench，守卫里再也拿不到真实目标路径。
const NotFound = { render: () => h('div', { style: 'padding:48px;color:#909399;text-align:center' }, '页面不存在或无权限') }

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/login/index.vue'), meta: { title: '登录' } },
  {
    path: '/',
    name: 'root',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/workbench',
    children: [
      { path: 'workbench', name: '/workbench', component: () => import('../views/portal/workbench/index.vue'), meta: { title: '工作台' } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'notfound', component: NotFound, meta: { title: '404' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let dynamicRegistered = false

function registerDynamicRoutes(menus) {
  const flat = []
  const walk = list => (list || []).forEach(m => {
    if (m.type === 'M' && m.component && compMap[m.component]) {
      flat.push({
        path: m.path,
        name: m.path,
        component: compMap[m.component],
        meta: { title: m.name, icon: m.icon }
      })
    } else if (m.children && m.children.length) {
      walk(m.children)
    }
  })
  walk(menus)
  // 注册到主布局（名为 root）下，保证共享同一布局实例
  flat.forEach(r => {
    if (!router.hasRoute(r.name)) router.addRoute('root', r)
  })
  dynamicRegistered = true
}

router.beforeEach(async to => {
  const store = useUserStore()
  if (to.path === '/login') {
    if (store.token) return '/workbench'
    return true
  }
  if (!store.token) return '/login'
  if (!dynamicRegistered) {
    try {
      const menus = await store.loadMenus()
      registerDynamicRoutes(menus)
      return to.fullPath // 重新解析
    } catch (e) {
      store.logout()
      return '/login'
    }
  }
  return true
})

export default router
