<template>
  <div class="kb-graph" ref="container">
    <div class="graph-toolbar">
      <div class="graph-legend">
        <span v-for="(c, k) in categoryColors" :key="k" class="legend-item">
          <span class="legend-dot" :style="{ background: c }" /> {{ k }}
        </span>
      </div>
      <div class="graph-hint">拖拽节点 · 滚轮缩放 · 双击查看详情</div>
    </div>
    <svg ref="svg" class="graph-svg"></svg>
    <div v-if="tooltip" class="graph-tooltip" :style="tooltipStyle">
      <div class="tt-title">{{ tooltip.title }}</div>
      <div class="tt-cat">{{ tooltip.category }}</div>
      <div v-if="tooltip.wikiPath" class="tt-path">{{ tooltip.wikiPath }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import * as d3 from 'd3'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] }
})
const emit = defineEmits(['node-click'])

const container = ref(null)
const svg = ref(null)
const tooltip = ref(null)
const tooltipStyle = ref({})

const categoryColors = {
  '制度': '#185fa5',
  '指标': '#0f8a5f',
  '案例': '#b8860b'
}

onMounted(() => {
  render()
})

watch(() => [props.nodes, props.edges], () => {
  nextTick(render)
}, { deep: true })

function render() {
  if (!svg.value || !props.nodes.length) return
  const width = container.value.clientWidth
  const height = Math.max(500, container.value.clientHeight)

  const sel = d3.select(svg.value)
  sel.selectAll('*').remove()

  const nodes = props.nodes.map(n => ({ ...n }))
  const links = props.edges.map(e => ({ ...e }))

  const simulation = d3.forceSimulation(nodes)
    .force('link', d3.forceLink(links).id(d => d.id).distance(180))
    .force('charge', d3.forceManyBody().strength(-400))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(50))

  const g = sel.append('g')

  // Zoom
  const zoom = d3.zoom()
    .scaleExtent([0.3, 3])
    .on('zoom', (event) => g.attr('transform', event.transform))
  sel.call(zoom)

  // Links
  const link = g.append('g')
    .selectAll('line')
    .data(links)
    .join('line')
    .attr('stroke', '#94a3b8')
    .attr('stroke-width', 1.5)
    .attr('stroke-dasharray', d => d.relationType === 'PREREQUISITE' ? '6,3' : null)
    .attr('stroke-opacity', 0.6)

  // Link labels
  const linkLabel = g.append('g')
    .selectAll('text')
    .data(links)
    .join('text')
    .text(d => d.label || '')
    .attr('font-size', 10)
    .attr('fill', '#64748b')
    .attr('text-anchor', 'middle')
    .attr('dy', -4)

  // Arrow markers
  sel.append('defs').append('marker')
    .attr('id', 'arrowhead').attr('viewBox', '0 -5 10 10')
    .attr('refX', 28).attr('refY', 0)
    .attr('markerWidth', 6).attr('markerHeight', 6)
    .attr('orient', 'auto')
    .append('path').attr('d', 'M0,-5L10,0L0,5')

  // Nodes
  const node = g.append('g')
    .selectAll('g')
    .data(nodes)
    .join('g')
    .call(d3.drag()
      .on('start', (event, d) => { if (!event.active) simulation.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y })
      .on('drag', (event, d) => { d.fx = event.x; d.fy = event.y })
      .on('end', (event, d) => { if (!event.active) simulation.alphaTarget(0); d.fx = null; d.fy = null }))

  // Node circles
  node.append('circle')
    .attr('r', 22)
    .attr('fill', d => categoryColors[d.category] || '#64748b')
    .attr('stroke', '#fff')
    .attr('stroke-width', 2)
    .attr('opacity', 0.9)

  // Node glow
  node.append('circle')
    .attr('r', 28)
    .attr('fill', 'none')
    .attr('stroke', d => categoryColors[d.category] || '#64748b')
    .attr('stroke-width', 1)
    .attr('opacity', 0.2)

  // Node labels (category icon)
  node.append('text')
    .text(d => (d.category || '?').charAt(0))
    .attr('text-anchor', 'middle')
    .attr('dy', 5)
    .attr('font-size', 13)
    .attr('font-weight', '700')
    .attr('fill', '#fff')

  // Node title below
  node.append('text')
    .text(d => d.title?.length > 6 ? d.title.slice(0, 6) + '…' : d.title)
    .attr('text-anchor', 'middle')
    .attr('dy', 38)
    .attr('font-size', 11)
    .attr('fill', '#334155')
    .attr('font-weight', '500')

  // Hover + click
  node.on('mouseover', (event, d) => {
    tooltip.value = d
    tooltipStyle.value = { left: event.offsetX + 12 + 'px', top: event.offsetY - 10 + 'px' }
    node.select('circle').attr('opacity', n => n.id === d.id ? 1 : 0.4)
    link.attr('stroke-opacity', l => (l.source.id === d.id || l.target.id === d.id) ? 1 : 0.1)
    linkLabel.attr('opacity', l => (l.source.id === d.id || l.target.id === d.id) ? 1 : 0.1)
  })
  node.on('mouseout', () => {
    tooltip.value = null
    node.select('circle').attr('opacity', 0.9)
    link.attr('stroke-opacity', 0.6)
    linkLabel.attr('opacity', 1)
  })
  node.on('dblclick', (event, d) => {
    event.stopPropagation()
    emit('node-click', d)
  })

  // Tick
  simulation.on('tick', () => {
    link
      .attr('x1', d => d.source.x).attr('y1', d => d.source.y)
      .attr('x2', d => d.target.x).attr('y2', d => d.target.y)
    linkLabel
      .attr('x', d => (d.source.x + d.target.x) / 2)
      .attr('y', d => (d.source.y + d.target.y) / 2)
    node.attr('transform', d => `translate(${d.x},${d.y})`)
  })
}
</script>

<style scoped>
.kb-graph {
  width: 100%; height: 100%; min-height: 520px;
  position: relative; background: #f8fafc; border-radius: 12px;
  border: 1px solid #e2e8f0; overflow: hidden;
}
.graph-toolbar {
  position: absolute; top: 12px; left: 16px; right: 16px;
  display: flex; justify-content: space-between; align-items: center;
  z-index: 10; pointer-events: none;
}
.graph-legend { display: flex; gap: 14px; }
.legend-item { display: flex; align-items: center; gap: 5px; font-size: 12px; color: #475569; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; }
.graph-hint { font-size: 11px; color: #94a3b8; }
.graph-svg { width: 100%; height: 100%; }
.graph-tooltip {
  position: absolute; z-index: 20; pointer-events: none;
  background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px 14px; box-shadow: 0 4px 12px rgba(0,0,0,.08);
  max-width: 240px;
}
.tt-title { font-size: 13px; font-weight: 600; color: #1e293b; }
.tt-cat { font-size: 11px; color: #64748b; margin-top: 3px; }
.tt-path { font-size: 11px; color: #185fa5; margin-top: 3px; font-family: monospace; }
</style>
