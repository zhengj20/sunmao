<template>
  <svg
    class="ra" :class="[`ra-${theme}`, { 'ra-working': working }]"
    :style="{ width: size + 'px', height: size + 'px' }"
    viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg"
  >
    <defs>
      <linearGradient :id="uid + '-head'" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#ffffff" stop-opacity="0.95"/>
        <stop offset="100%" :stop-color="theme.c2" stop-opacity="0.55"/>
      </linearGradient>
      <linearGradient :id="uid + '-body'" x1="0" y1="0" x2="0" y2="1">
        <stop :offset="'0%'" :stop-color="theme.c1"/>
        <stop :offset="'100%'" :stop-color="theme.c3"/>
      </linearGradient>
      <radialGradient :id="uid + '-glow'">
        <stop offset="0%" :stop-color="theme.c1" stop-opacity="0.55"/>
        <stop offset="100%" :stop-color="theme.c1" stop-opacity="0"/>
      </radialGradient>
      <clipPath :id="uid + '-visor'">
        <rect x="35" y="37" width="50" height="24" rx="11"/>
      </clipPath>
    </defs>

    <!-- 底盘光晕 -->
    <circle cx="60" cy="62" r="56" :fill="`url(#${uid}-glow)`"/>
    <!-- 工作中：旋转虚线环 -->
    <circle class="ra-ring" cx="60" cy="62" r="54" fill="none" :stroke="theme.c1"
            stroke-width="2.5" stroke-dasharray="14 10" stroke-linecap="round"/>

    <g class="ra-float">
      <!-- 天线 -->
      <line x1="60" y1="20" x2="60" y2="30" :stroke="theme.c3" stroke-width="3" stroke-linecap="round"/>
      <circle class="ra-dot" cx="60" cy="16" r="4.5" :fill="theme.c1"/>
      <circle class="ra-dot-halo" cx="60" cy="16" r="4.5" fill="none" :stroke="theme.c1" stroke-width="1.5"/>

      <!-- 侧耳 -->
      <rect x="24" y="44" width="8" height="18" rx="4" :fill="theme.c3"/>
      <rect x="88" y="44" width="8" height="18" rx="4" :fill="theme.c3"/>

      <!-- 头 -->
      <rect x="30" y="28" width="60" height="52" rx="16" :fill="`url(#${uid}-head)`"
            stroke="#ffffff" stroke-opacity="0.7" stroke-width="1.5"/>
      <rect x="30" y="28" width="60" height="52" rx="16" fill="none" :stroke="theme.c1" stroke-opacity="0.35" stroke-width="1.5"/>

      <!-- 面罩 -->
      <rect x="35" y="37" width="50" height="24" rx="11" fill="#0e1c2e"/>
      <g :clip-path="`url(#${uid}-visor)`">
        <circle class="ra-eye ra-eye-l" cx="49" cy="49" r="5" fill="#8fd8ff"/>
        <circle class="ra-eye ra-eye-r" cx="71" cy="49" r="5" fill="#8fd8ff"/>
        <circle cx="47.5" cy="47.5" r="1.6" fill="#ffffff"/>
        <circle cx="69.5" cy="47.5" r="1.6" fill="#ffffff"/>
        <!-- 工作中：扫描光条 -->
        <rect class="ra-scan" x="30" y="38" width="10" height="22" :fill="theme.c1" opacity="0.55"/>
      </g>

      <!-- 腮红 + 嘴 -->
      <circle cx="41" cy="66" r="3.2" :fill="theme.c1" opacity="0.4"/>
      <circle cx="79" cy="66" r="3.2" :fill="theme.c1" opacity="0.4"/>
      <rect x="54" y="71" width="12" height="3" rx="1.5" fill="#0e1c2e" opacity="0.55"/>

      <!-- 颈 -->
      <rect x="52" y="80" width="16" height="7" rx="3" :fill="theme.c3"/>

      <!-- 身体 -->
      <path d="M36 87 h48 a8 8 0 0 1 8 8 v6 a8 8 0 0 1 -8 8 h-48 a8 8 0 0 1 -8 -8 v-6 a8 8 0 0 1 8 -8 z"
            transform="translate(4 0)" :fill="`url(#${uid}-body)`"/>
      <path d="M36 87 h48 a8 8 0 0 1 8 8 v6 a8 8 0 0 1 -8 8 h-48 a8 8 0 0 1 -8 -8 v-6 a8 8 0 0 1 8 -8 z"
            transform="translate(4 0)" fill="none" stroke="#ffffff" stroke-opacity="0.5" stroke-width="1.2"/>

      <!-- 胸口徽章（角色标识） -->
      <circle cx="62" cy="97" r="9.5" fill="#ffffff" opacity="0.92"/>
      <g :transform="`translate(62 97)`">
        <!-- 招采：公文包 -->
        <g v-if="theme === THEMES.proc">
          <rect x="-6" y="-3.5" width="12" height="8.5" rx="2" :fill="theme.c1"/>
          <path d="M-2.5 -3.5 v-1.8 a1.5 1.5 0 0 1 1.5 -1.5 h2 a1.5 1.5 0 0 1 1.5 1.5 v1.8" fill="none" :stroke="theme.c1" stroke-width="1.6"/>
          <rect x="-1.4" y="-0.4" width="2.8" height="2.2" rx="0.6" fill="#ffffff"/>
        </g>
        <!-- 成本 / 财务：¥ -->
        <text v-else-if="theme === THEMES.cost || theme === THEMES.fin" x="0" y="4.5"
              text-anchor="middle" font-size="12" font-weight="800" :fill="theme.c1"
              font-family="'PingFang SC','Microsoft YaHei',sans-serif">¥</text>
        <!-- 营销：扩音器 -->
        <g v-else-if="theme === THEMES.sal">
          <path d="M-6.5 -1.5 h3.4 l4.6 -3.4 v10.8 l-4.6 -3.4 h-3.4 z" :fill="theme.c1"/>
          <path d="M5.4 -3.2 a5.4 5.4 0 0 1 0 7.4" fill="none" :stroke="theme.c1" stroke-width="1.7" stroke-linecap="round"/>
        </g>
        <!-- 法务：天平 -->
        <g v-else-if="theme === THEMES.legal">
          <line x1="0" y1="-5" x2="0" y2="4.5" :stroke="theme.c1" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="-6" y1="-3.5" x2="6" y2="-3.5" :stroke="theme.c1" stroke-width="1.5" stroke-linecap="round"/>
          <path d="M-8 -3.5 Q-6 0.5 -4 -3.5" :fill="theme.c1" opacity="0.7"/>
          <path d="M4 -3.5 Q6 0.5 8 -3.5" :fill="theme.c1" opacity="0.7"/>
          <rect x="-3" y="4" width="6" height="1.5" rx="0.75" :fill="theme.c1"/>
        </g>
        <!-- 项目工程师：六角螺母 -->
        <g v-else-if="theme === THEMES.proj">
          <polygon points="0,-5.5 4.76,-2.75 4.76,2.75 0,5.5 -4.76,2.75 -4.76,-2.75"
                   fill="none" :stroke="theme.c1" stroke-width="2"/>
          <circle r="2" :fill="theme.c1"/>
        </g>
        <!-- 通用：星 -->
        <path v-else d="M0 -6.4 L1.9 -2 6.4 -1.9 2.9 1.1 4.1 5.8 0 3.2 -4.1 5.8 -2.9 1.1 -6.4 -1.9 -1.9 -2 Z" :fill="theme.c1"/>
      </g>
    </g>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const THEMES = {
  proc: { c1: '#185fa5', c2: '#f5b942', c3: '#0c2d4e' },
  cost: { c1: '#0f8a5f', c2: '#7fd4a8', c3: '#075c3e' },
  fin: { c1: '#6b4fd8', c2: '#f5c542', c3: '#43309b' },
  sal: { c1: '#e8622c', c2: '#ffb38a', c3: '#a83c12' },
  legal: { c1: '#b8860b', c2: '#f5d678', c3: '#7a5a0a' },
  proj: { c1: '#5a7d9a', c2: '#b0cfe0', c3: '#3a5a75' },
  custom: { c1: '#0e9aa7', c2: '#7fd6dd', c3: '#085f68' }
}

const props = defineProps({
  theme: { type: String, default: 'custom' },
  working: { type: Boolean, default: false },
  size: { type: Number, default: 96 }
})

const theme = computed(() => THEMES[props.theme] || THEMES.custom)
const uid = Math.random().toString(36).slice(2, 9)
</script>

<style scoped>
.ra { display: block; overflow: visible; }
.ra-ring { opacity: 0; transform-origin: 60px 62px; }
.ra-working .ra-ring { opacity: 0.85; animation: ra-spin 6s linear infinite; }

.ra-float { animation: ra-float 3.6s ease-in-out infinite; }
.ra-working .ra-float { animation: ra-float 1.6s ease-in-out infinite; }

.ra-eye { transform-origin: center; animation: ra-blink 4.2s infinite; }
.ra-working .ra-eye { animation: ra-blink 0.9s infinite; }

.ra-dot-halo { opacity: 0; transform-origin: 60px 16px; }
.ra-working .ra-dot-halo { animation: ra-pulse 1.2s ease-out infinite; }

.ra-scan { opacity: 0; }
.ra-working .ra-scan { animation: ra-scan 1.4s ease-in-out infinite; }

@keyframes ra-spin { to { transform: rotate(360deg); } }
@keyframes ra-float { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-2.5px); } }
@keyframes ra-blink { 0%, 92%, 100% { transform: scaleY(1); } 95% { transform: scaleY(0.12); } }
@keyframes ra-pulse { 0% { transform: scale(1); opacity: 0.9; } 100% { transform: scale(2.6); opacity: 0; } }
@keyframes ra-scan { 0% { transform: translateX(0); opacity: 0; } 15% { opacity: 0.6; } 85% { opacity: 0.6; } 100% { transform: translateX(44px); opacity: 0; } }
</style>
