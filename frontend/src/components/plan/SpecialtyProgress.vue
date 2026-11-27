<template>
  <div class="sp-list">
    <div v-for="r in rows" :key="r.specialty" class="sp-row">
      <div class="sp-name">{{ r.specialty }}</div>
      <div class="sp-mid">
        <el-progress :percentage="Math.min(100, Number(r.avgProgress) || 0)" :stroke-width="10" :show-text="false" />
        <div class="sp-sub">
          <span>{{ r.taskCount }} 项</span>
          <span v-if="r.currentNode">当前节点：{{ r.currentNode }}</span>
          <span v-else class="dim">暂无进行中</span>
        </div>
      </div>
      <div class="sp-pct num">{{ Math.round(Number(r.avgProgress) || 0) }}%</div>
      <div class="sp-lights">
        <span class="lg g" title="正常">{{ r.green }}</span>
        <span class="lg y" title="预警">{{ r.yellow }}</span>
        <span class="lg r" title="滞后">{{ r.red }}</span>
      </div>
    </div>
    <el-empty v-if="!rows.length" description="暂无专项计划数据" :image-size="60" />
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Array, default: () => [] }
})
</script>

<style scoped>
.sp-list { padding: 2px 0; }
.sp-row { display: grid; grid-template-columns: 52px 1fr 48px 88px; gap: 12px; align-items: center; padding: 10px 0; border-bottom: 1px dashed #eef0f3; }
.sp-row:last-child { border-bottom: none; }
.sp-name { font-size: 13px; font-weight: 700; color: #0c447c; }
.sp-sub { display: flex; justify-content: space-between; font-size: 11.5px; color: #6b7280; margin-top: 4px; }
.dim { color: #c0c4cc; }
.sp-pct { font-size: 13px; font-weight: 700; color: #1f2937; text-align: right; }
.num { font-variant-numeric: tabular-nums; }
.sp-lights { display: flex; gap: 4px; }
.lg { min-width: 22px; text-align: center; border-radius: 10px; padding: 1px 6px; font-size: 11.5px; font-weight: 700; font-variant-numeric: tabular-nums; }
.lg.g { background: #f0f9ec; color: #67c23a; }
.lg.y { background: #fdf6ec; color: #e6a23c; }
.lg.r { background: #fef0f0; color: #f56c6c; }
</style>
