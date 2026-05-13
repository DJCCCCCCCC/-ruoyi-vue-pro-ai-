<template>
  <ContentWrap class="panel image-ocr-panel">
    <div class="panel-head">
      <div>
        <p class="panel-kicker">Image &amp; OCR</p>
        <h3>图片内容分析</h3>
        <p class="panel-desc">
          专项展示 OCR 全文与分图结果；可上传截图单独跑 OCR，并选用大模型生成「图中文字含义与潜在风险」说明（与完整支付评估接口独立）。
        </p>
      </div>
    </div>

    <div class="image-ocr-grid">
      <article class="image-ocr-card standalone-card">
        <h4>上传图片 · 专项分析</h4>
        <p class="hint">支持多张；单次最多 5 张（与后端配置一致）。需配置 Gitee OCR 与 DeepSeek 方可分别获得识别与解读。</p>
        <el-upload
          drag
          multiple
          :auto-upload="false"
          accept="image/*"
          :show-file-list="true"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将图片拖到此处，或 <em>点击选择</em></div>
        </el-upload>
        <div class="standalone-actions">
          <el-checkbox v-model="includeLlmInsight">OCR 后生成 LLM 风险解读</el-checkbox>
          <el-button type="primary" :loading="standaloneLoading" :disabled="pendingFiles.length === 0" @click="runStandalone">
            开始分析
          </el-button>
          <el-button v-if="standaloneResult" link type="primary" @click="clearStandalone">清空结果</el-button>
        </div>
        <div v-if="standaloneResult" v-loading="standaloneLoading" class="standalone-result">
          <div class="stat-row">
            <el-tag size="small">检测 {{ standaloneResult.embeddedImageCount ?? 0 }} 张</el-tag>
            <el-tag size="small" :type="standaloneResult.ocrServiceEnabled ? 'success' : 'info'">
              OCR {{ standaloneResult.ocrServiceEnabled ? '已开启' : '未开启' }}
            </el-tag>
            <el-tag size="small">调用 {{ standaloneResult.ocrApiCallCount ?? 0 }} 次</el-tag>
            <el-tag size="small">有效段 {{ standaloneResult.ocrValidTextCount ?? 0 }}</el-tag>
          </div>
          <p v-if="standaloneResult.imageOcrSummary" class="summary-line">{{ standaloneResult.imageOcrSummary }}</p>
          <div v-if="standaloneResult.llmImageContentNarrative" class="llm-block">
            <span class="block-label">LLM 解读</span>
            <pre class="ocr-pre narrative-pre">{{ standaloneResult.llmImageContentNarrative }}</pre>
          </div>
          <div v-if="perImageBlocksStandalone.length" class="per-image-list">
            <div v-for="(b, i) in perImageBlocksStandalone" :key="'s-' + i" class="per-image-item">
              <div class="per-image-head">第 {{ i + 1 }} 张</div>
              <pre class="ocr-pre">{{ b }}</pre>
            </div>
          </div>
          <el-collapse
            v-if="perImageBlocksStandalone.length && standaloneResult.multimodalImageOcrMerged"
            class="merged-collapse"
          >
            <el-collapse-item title="查看完整合并 OCR" name="merged">
              <pre class="ocr-pre merged-pre">{{ standaloneResult.multimodalImageOcrMerged }}</pre>
            </el-collapse-item>
          </el-collapse>
          <div v-if="standaloneResult.multimodalImageOcrMerged && !perImageBlocksStandalone.length" class="merged-only">
            <span class="block-label">合并 OCR</span>
            <pre class="ocr-pre">{{ standaloneResult.multimodalImageOcrMerged }}</pre>
          </div>
        </div>
      </article>

      <article class="image-ocr-card record-card">
        <h4>当前选中工单</h4>
        <p v-if="!recordId" class="muted">请在下方「分析记录」中点击「查看」选择一条评估工单。</p>
        <template v-else>
          <p class="record-id">工单 #{{ recordId }}</p>
          <div v-if="recordHasOcr" class="record-body">
            <div class="stat-row">
              <el-tag v-if="recordEmbeddedGuess > 0" size="small">约 {{ recordEmbeddedGuess }} 处图片字段</el-tag>
              <el-tag size="small" type="success">已落库 OCR 文本</el-tag>
            </div>
            <div v-if="recordPerImageTexts.length" class="per-image-list">
              <div v-for="(t, i) in recordPerImageTexts" :key="'r-' + i" class="per-image-item">
                <div class="per-image-head">第 {{ i + 1 }} 张（OCR）</div>
                <pre class="ocr-pre">{{ t }}</pre>
              </div>
            </div>
            <el-collapse v-if="recordPerImageTexts.length && recordMerged" class="merged-collapse">
              <el-collapse-item title="查看完整合并 OCR" name="rec-merged">
                <pre class="ocr-pre merged-pre">{{ recordMerged }}</pre>
              </el-collapse-item>
            </el-collapse>
            <div v-if="recordMerged && !recordPerImageTexts.length" class="merged-only">
              <span class="block-label">合并 OCR</span>
              <pre class="ocr-pre">{{ recordMerged }}</pre>
            </div>
          </div>
          <el-empty v-else description="该工单 paymentData 中无 multimodalImageOcr 字段（可能未上传图片、OCR 未开启或评估发生在该功能之前）" />
        </template>
      </article>
    </div>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import { computed, ref } from 'vue'
import { analyzePayRiskImageOcr, type PayRiskImageOcrAnalyzeRespVO } from '@/api/pay/risk/assess'
import { useMessage } from '@/hooks/web/useMessage'

const props = defineProps<{
  paymentData?: Record<string, any> | null
  recordId?: number | null
}>()

const message = useMessage()
const pendingFiles = ref<UploadUserFile[]>([])
const includeLlmInsight = ref(true)
const standaloneLoading = ref(false)
const standaloneResult = ref<PayRiskImageOcrAnalyzeRespVO | null>(null)

const recordMerged = computed(() => {
  const m = props.paymentData?.multimodalImageOcrMerged
  return typeof m === 'string' && m.trim() ? m.trim() : ''
})

const recordPerImageTexts = computed(() => {
  const arr = props.paymentData?.multimodalImageOcrTexts
  if (!Array.isArray(arr)) return []
  return arr.map((x: unknown) => String(x || '').trim()).filter(Boolean)
})

const recordHasOcr = computed(() => Boolean(recordMerged.value || recordPerImageTexts.value.length))

const recordEmbeddedGuess = computed(() => {
  const pd = props.paymentData
  if (!pd) return 0
  let n = 0
  const walk = (v: unknown): void => {
    if (v == null) return
    if (typeof v === 'string' && v.startsWith('data:image/') && v.includes(';base64,')) {
      n++
      return
    }
    if (Array.isArray(v)) {
      v.forEach(walk)
      return
    }
    if (typeof v === 'object') {
      Object.values(v as object).forEach(walk)
    }
  }
  walk(pd)
  return n
})

const perImageBlocksStandalone = computed(() => {
  const list = standaloneResult.value?.multimodalImageOcrTexts
  if (Array.isArray(list) && list.length) return list.map((x) => String(x || '').trim()).filter(Boolean)
  return []
})

const handleFileChange = (_file: UploadFile, fileList: UploadFiles) => {
  pendingFiles.value = fileList as UploadUserFile[]
}

const handleFileRemove = (_file: UploadFile, fileList: UploadFiles) => {
  pendingFiles.value = fileList as UploadUserFile[]
}

const readFileAsDataUrl = (raw: File) =>
  new Promise<string>((resolve, reject) => {
    const fr = new FileReader()
    fr.onload = () => resolve(String(fr.result || ''))
    fr.onerror = () => reject(new Error('read failed'))
    fr.readAsDataURL(raw)
  })

const extractApiError = (e: unknown): string => {
  const any = e as any
  return any?.response?.data?.msg ?? any?.response?.data?.message ?? any?.message ?? '分析失败'
}

const runStandalone = async () => {
  const files = pendingFiles.value.map((f) => f.raw).filter((x): x is File => x instanceof File)
  if (!files.length) {
    message.warning('请先选择图片文件')
    return
  }
  standaloneLoading.value = true
  standaloneResult.value = null
  try {
    const urls: string[] = []
    for (const f of files.slice(0, 5)) {
      urls.push(await readFileAsDataUrl(f))
    }
    const data = await analyzePayRiskImageOcr({ imageDataUrls: urls, includeLlmInsight: includeLlmInsight.value })
    standaloneResult.value = data
    if (!data.ocrServiceEnabled) {
      message.warning('OCR 服务未开启或未配置，仅返回统计说明')
    } else if (!data.ocrValidTextCount) {
      message.info('OCR 已完成，但未得到有效文字')
    } else {
      message.success('分析完成')
    }
  } catch (e: unknown) {
    message.error(extractApiError(e))
  } finally {
    standaloneLoading.value = false
  }
}

const clearStandalone = () => {
  standaloneResult.value = null
  pendingFiles.value = []
}
</script>

<style scoped lang="scss">
.image-ocr-panel {
  margin-bottom: 16px;
}

.panel-kicker {
  margin: 0 0 4px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--el-text-color-secondary);
}

.panel-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.image-ocr-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 16px;
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .image-ocr-grid {
    grid-template-columns: 1fr;
  }
}

.image-ocr-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px 16px;
  background: var(--el-fill-color-blank);

  h4 {
    margin: 0 0 8px;
    font-size: 15px;
  }

  .hint {
    margin: 0 0 12px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.45;
  }
}

.standalone-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.standalone-result {
  margin-top: 16px;
}

.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.summary-line {
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
  margin: 0 0 12px;
}

.block-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
}

.ocr-pre {
  margin: 0;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}

.narrative-pre {
  max-height: 280px;
  border: 1px solid var(--el-color-primary-light-7);
}

.llm-block {
  margin-bottom: 14px;
}

.per-image-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.per-image-head {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--el-text-color-regular);
}

.merged-only {
  margin-top: 4px;
}

.record-id {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.merged-collapse {
  margin-top: 12px;
}

.merged-pre {
  max-height: 400px;
}
</style>
