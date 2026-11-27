<template>
  <div class="doc-preview">
    <div v-if="loading" class="doc-state">文档加载中…</div>
    <div v-else-if="error" class="doc-state doc-error">
      <p>{{ error }}</p>
      <p class="doc-hint">可尝试「下载原文件」后用本地 Word 打开。</p>
    </div>
    <div v-else class="doc-body" v-html="html"></div>
  </div>
</template>

<script setup>
import * as mammoth from 'mammoth'
import { onMounted, ref, watch } from 'vue'

const props = defineProps({
  // 形如 /files/templates/xxx.docx
  fileUrl: { type: String, required: true }
})

const loading = ref(true)
const error = ref('')
const html = ref('')

const load = async () => {
  loading.value = true
  error.value = ''
  html.value = ''
  try {
    const resp = await fetch(props.fileUrl)
    if (!resp.ok) throw new Error('文件加载失败（HTTP ' + resp.status + '）')
    const arrayBuffer = await resp.arrayBuffer()
    const result = await mammoth.convertToHtml({ arrayBuffer })
    html.value = result.value || '<p>（空文档）</p>'
  } catch (e) {
    error.value = e.message || '预览失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.fileUrl, load)
</script>

<style scoped>
.doc-preview {
  height: 100%;
  overflow: auto;
  background: #fff;
  padding: 24px 32px;
}
.doc-state {
  text-align: center;
  color: #909399;
  padding: 60px 0;
}
.doc-error { color: #c45656; }
.doc-hint { font-size: 12px; color: #a0a4ab; margin-top: 8px; }
.doc-body { font-size: 14px; line-height: 1.9; color: #303133; max-width: 820px; margin: 0 auto; }
.doc-body :deep(h1), .doc-body :deep(h2), .doc-body :deep(h3) { margin: 18px 0 10px; }
.doc-body :deep(table) { border-collapse: collapse; width: 100%; margin: 12px 0; }
.doc-body :deep(td), .doc-body :deep(th) { border: 1px solid #dcdfe6; padding: 6px 10px; }
.doc-body :deep(p) { margin: 6px 0; }
</style>
