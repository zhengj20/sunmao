<template>
  <div class="login-page">
    <div class="brand">
      <div class="brand-title">榫卯 SunMao</div>
      <div class="brand-sub">投资 · 招采 · 成本 · 资金 · 计划 · 审批 —— 一体化数字底座</div>
    </div>
    <el-card class="login-card" shadow="always">
      <div class="card-title">账号登录</div>
      <el-form :model="form" size="large" @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="login-btn" size="large" :loading="loading" @click="onLogin">
          登 录
        </el-button>
      </el-form>
      <el-divider style="margin: 18px 0 10px" />
      <div class="tips">
        <div>演示账号（密码见括号）：admin(admin123) 集团管理员</div>
        <div>costmgr / procmgr / finmgr / viewer（均为 123456）</div>
      </div>
    </el-card>
    <div class="footer">榫卯 SunMao · 房地产行业一体化平台 · AGPL-3.0 开源</div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

const onLogin = async () => {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码')
  loading.value = true
  try {
    await store.login({ ...form })
    ElMessage.success('登录成功')
    router.push('/workbench')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  background: linear-gradient(135deg, #042c53 0%, #185fa5 60%, #2a74bd 100%);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 22px;
}
.brand { text-align: center; color: #fff; }
.brand-title { font-size: 30px; font-weight: 700; letter-spacing: 3px; }
.brand-sub { margin-top: 8px; font-size: 13.5px; opacity: .85; letter-spacing: 1px; }
.login-card { width: 380px; border-radius: 10px; }
.card-title { font-size: 17px; font-weight: 700; color: #0c447c; margin-bottom: 18px; text-align: center; }
.login-btn { width: 100%; }
.tips { font-size: 12px; color: #6b7280; line-height: 1.9; text-align: center; }
.footer { position: absolute; bottom: 18px; color: rgba(255,255,255,.55); font-size: 12px; letter-spacing: 1px; }
</style>
