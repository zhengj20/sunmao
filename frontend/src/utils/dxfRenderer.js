// DXF → Canvas 渲染器：解析 DXF（dxf-parser），按实体绘制到画布（自动适配 + Y 轴翻转）。
// 弧/椭圆用采样折线绘制，规避 Y 翻转下的方向问题。文本按字高缩放（保证可读下限）。
import DxfParser from 'dxf-parser'

const DEF_COLOR = '#1a1a1a'

function colorOf(e) {
  return (e && typeof e.color === 'string' && /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(e.color)) ? e.color : DEF_COLOR
}

function entityPoints(e) {
  const pts = []
  const push = p => { if (p && typeof p.x === 'number' && typeof p.y === 'number') pts.push([p.x, p.y]) }
  if (Array.isArray(e.vertices)) e.vertices.forEach(push)
  if (Array.isArray(e.points)) e.points.forEach(push)
  if (Array.isArray(e.controlPoints)) e.controlPoints.forEach(push)
  if (Array.isArray(e.fitPoints)) e.fitPoints.forEach(push)
  if (e.center) push(e.center)
  if (e.startPoint) push(e.startPoint)
  if (e.endPoint) push(e.endPoint)
  if (e.position) push(e.position)
  if (e.majorAxisEndPoint) push(e.majorAxisEndPoint)
  if (e.radius) {
    const c = e.center
    if (c) pts.push([c.x - e.radius, c.y], [c.x + e.radius, c.y], [c.x, c.y - e.radius], [c.x, c.y + e.radius])
  }
  return pts
}

export function computeBounds(entities) {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const e of (entities || [])) {
    for (const [x, y] of entityPoints(e)) {
      if (x < minX) minX = x; if (y < minY) minY = y
      if (x > maxX) maxX = x; if (y > maxY) maxY = y
    }
  }
  if (!isFinite(minX)) { minX = 0; minY = 0; maxX = 100; maxY = 100 }
  return { minX, minY, maxX, maxY }
}

function makeTransform(bounds, width, height) {
  const pad = Math.max(10, Math.min(width, height) * 0.02)
  const dw = (bounds.maxX - bounds.minX) || 1
  const dh = (bounds.maxY - bounds.minY) || 1
  const scale = Math.min((width - 2 * pad) / dw, (height - 2 * pad) / dh)
  const offX = (width - dw * scale) / 2
  const offY = (height - dh * scale) / 2
  const sx = x => (x - bounds.minX) * scale + offX
  const sy = y => height - ((y - bounds.minY) * scale + offY)
  return { sx, sy, scale }
}

/** 解析 DXF 文本 → { entities, bounds } */
export function parseDxf(text) {
  const parser = new DxfParser()
  const r = parser.parse(text)
  const entities = (r && r.entities) || []
  return { entities, bounds: computeBounds(entities) }
}

function cleanMtext(t) {
  if (!t) return ''
  return String(t)
    .replace(/\\P/g, '\n')
    .replace(/\\[A-Za-z][0-9.]*[^P]?/g, '')
    .replace(/\\[^\n]/g, '')
    .trim()
}

function sampleArc(ctx, T, cx, cy, r, a0, a1) {
  if (a1 < a0) a1 += Math.PI * 2
  const steps = Math.max(12, Math.ceil((a1 - a0) / (Math.PI / 40)))
  ctx.beginPath()
  for (let i = 0; i <= steps; i++) {
    const a = a0 + (a1 - a0) * i / steps
    const x = cx + r * Math.cos(a)
    const y = cy + r * Math.sin(a)
    if (i === 0) ctx.moveTo(T.sx(x), T.sy(y)); else ctx.lineTo(T.sx(x), T.sy(y))
  }
  ctx.stroke()
}

function drawText(ctx, T, x, y, text, height) {
  if (!text) return
  const fs = Math.max(11, (height || 10) * T.scale)
  ctx.font = `${fs}px "Microsoft YaHei", Arial, sans-serif`
  ctx.textBaseline = 'alphabetic'
  const lines = String(text).split('\n')
  lines.forEach((ln, i) => ctx.fillText(ln, T.sx(x), T.sy(y) - i * fs))
}

/** 将 DXF 实体绘制到画布（width/height 为画布 CSS 像素，需已设置 devicePixelRatio 缩放） */
export function renderDxf(ctx, width, height, entities, bounds) {
  const T = makeTransform(bounds, width, height)
  const baseLw = Math.max(1, Math.min(1.4, T.scale * 0.6))
  ctx.save()
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'

  for (const e of entities) {
    if (!e || !e.type) continue
    ctx.strokeStyle = colorOf(e)
    ctx.fillStyle = ctx.strokeStyle
    ctx.lineWidth = baseLw

    switch (e.type) {
      case 'LINE': {
        const a = e.vertices && e.vertices[0], b = e.vertices && e.vertices[1]
        if (!a || !b) break
        ctx.beginPath(); ctx.moveTo(T.sx(a.x), T.sy(a.y)); ctx.lineTo(T.sx(b.x), T.sy(b.y)); ctx.stroke()
        break
      }
      case 'LWPOLYLINE':
      case 'POLYLINE': {
        const vs = (e.vertices || []).filter(v => v && typeof v.x === 'number')
        if (vs.length < 2) break
        ctx.beginPath(); ctx.moveTo(T.sx(vs[0].x), T.sy(vs[0].y))
        for (let i = 1; i < vs.length; i++) ctx.lineTo(T.sx(vs[i].x), T.sy(vs[i].y))
        if (e.shape) ctx.closePath()
        ctx.stroke()
        break
      }
      case 'CIRCLE': {
        if (!e.center || !e.radius) break
        ctx.beginPath(); ctx.arc(T.sx(e.center.x), T.sy(e.center.y), e.radius * T.scale, 0, Math.PI * 2); ctx.stroke()
        break
      }
      case 'ARC': {
        if (!e.center || !e.radius) break
        sampleArc(ctx, T, e.center.x, e.center.y, e.radius, e.startAngle || 0, e.endAngle || 0)
        break
      }
      case 'ELLIPSE': {
        if (!e.center || !e.majorAxisEndPoint) break
        const a = Math.atan2(e.majorAxisEndPoint.y - e.center.y, e.majorAxisEndPoint.x - e.center.x)
        const rx = Math.hypot(e.majorAxisEndPoint.x - e.center.x, e.majorAxisEndPoint.y - e.center.y)
        const ry = rx * (e.axisRatio || 1)
        let s0 = e.startAngle || 0, s1 = e.endAngle || 0
        if (s1 < s0) s1 += Math.PI * 2
        const steps = Math.max(16, Math.ceil((s1 - s0) / (Math.PI / 40)))
        ctx.beginPath()
        for (let i = 0; i <= steps; i++) {
          const t = s0 + (s1 - s0) * i / steps
          const ex = rx * Math.cos(t), ey = ry * Math.sin(t)
          const x = e.center.x + ex * Math.cos(a) - ey * Math.sin(a)
          const y = e.center.y + ex * Math.sin(a) + ey * Math.cos(a)
          if (i === 0) ctx.moveTo(T.sx(x), T.sy(y)); else ctx.lineTo(T.sx(x), T.sy(y))
        }
        ctx.stroke()
        break
      }
      case 'SOLID':
      case '3DFACE': {
        const vs = (e.points && e.points.length ? e.points : (e.vertices || [])).filter(v => v && typeof v.x === 'number')
        if (vs.length < 3) break
        ctx.beginPath(); ctx.moveTo(T.sx(vs[0].x), T.sy(vs[0].y))
        for (let i = 1; i < vs.length; i++) ctx.lineTo(T.sx(vs[i].x), T.sy(vs[i].y))
        ctx.closePath(); ctx.fill()
        break
      }
      case 'SPLINE': {
        const pts = (e.fitPoints && e.fitPoints.length ? e.fitPoints : (e.controlPoints || []))
          .filter(p => p && typeof p.x === 'number')
        if (pts.length < 2) break
        ctx.beginPath(); ctx.moveTo(T.sx(pts[0].x), T.sy(pts[0].y))
        for (let i = 1; i < pts.length; i++) ctx.lineTo(T.sx(pts[i].x), T.sy(pts[i].y))
        ctx.stroke()
        break
      }
      case 'POINT': {
        if (!e.position) break
        const x = T.sx(e.position.x), y = T.sy(e.position.y)
        ctx.beginPath(); ctx.moveTo(x - 4, y); ctx.lineTo(x + 4, y); ctx.moveTo(x, y - 4); ctx.lineTo(x, y + 4); ctx.stroke()
        break
      }
      case 'TEXT': {
        if (!e.startPoint) break
        drawText(ctx, T, e.startPoint.x, e.startPoint.y, e.text, e.textHeight)
        break
      }
      case 'MTEXT': {
        if (!e.position) break
        drawText(ctx, T, e.position.x, e.position.y, cleanMtext(e.text), e.height)
        break
      }
      default:
        break
    }
  }
  ctx.restore()
}
