<template>
  <ol class="run-steps">
    <li v-for="s in steps" :key="s.seq" :class="`rs-${(s.status || '').toLowerCase()}`">
      <span class="rs-dot">
        <el-icon v-if="s.status === 'SUCCESS'" class="rs-icon ok"><Check /></el-icon>
        <el-icon v-else-if="s.status === 'FAIL'" class="rs-icon bad"><Close /></el-icon>
        <el-icon v-else-if="s.status === 'WARN'" class="rs-icon warn"><WarningFilled /></el-icon>
        <span v-else class="rs-spin"></span>
      </span>
      <div class="rs-body">
        <div class="rs-head">
          <b>{{ s.title }}</b>
          <span v-if="s.ts" class="rs-ts">{{ s.ts }}</span>
        </div>
        <p v-if="s.detail" class="rs-detail">{{ s.detail }}</p>
      </div>
    </li>
  </ol>
</template>

<script setup>
import { computed } from 'vue'
import { Check, Close, WarningFilled } from '@element-plus/icons-vue'

const props = defineProps({
  steps: { type: String, default: '' } // 后端 steps JSON 字符串
})

const steps = computed(() => {
  try {
    const arr = props.steps ? JSON.parse(props.steps) : []
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
})
</script>

<style scoped>
.run-steps { margin: 0; padding: 0; list-style: none; position: relative; }
.run-steps li { position: relative; padding: 0 0 16px 34px; }
.run-steps li::before {
  content: ''; position: absolute; left: 12px; top: 22px; bottom: 0;
  width: 2px; background: #dbe7f3;
}
.run-steps li:last-child::before { display: none; }
.rs-dot {
  position: absolute; left: 0; top: 0; width: 26px; height: 26px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: #eef3f9; border: 2px solid #c9d8e8; color: #90a4b8;
}
.rs-icon { font-size: 14px; }
.rs-icon.ok { color: #2fa36b; }
.rs-icon.bad { color: #e05656; }
.rs-icon.warn { color: #e8a33d; }
.rs-spin {
  width: 12px; height: 12px; border-radius: 50%;
  border: 2px solid #b9cddd; border-top-color: #185fa5;
  animation: rs-rot 0.8s linear infinite;
}
.rs-success .rs-dot { background: #e9f7ef; border-color: #9fd8b8; }
.rs-fail .rs-dot { background: #fdecec; border-color: #f0a3a3; }
.rs-warn .rs-dot { background: #fdf4e3; border-color: #ecd39c; }
.rs-head { display: flex; align-items: baseline; gap: 8px; }
.rs-head b { font-size: 13.5px; color: #1f2d3d; }
.rs-ts { font-size: 11.5px; color: #9aa7b5; font-family: "SF Mono", Consolas, monospace; }
.rs-detail { margin: 4px 0 0; font-size: 12.5px; line-height: 1.65; color: #5c6b7c; word-break: break-all; }
@keyframes rs-rot { to { transform: rotate(360deg); } }
</style>
