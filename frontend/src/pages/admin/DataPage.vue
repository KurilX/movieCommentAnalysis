<template>
  <div class="data-page">
    <h2>数据管理</h2>

    <el-card class="actions">
      <el-button type="primary" @click="rerunAll" :loading="rerunning">
        一键重跑全库
      </el-button>
      <span class="hint">重跑会覆盖所有已有分析结果（换模型后使用）</span>
    </el-card>

    <el-card class="model-card">
      <template #header>分词模型（关键词提取用）</template>
      <div class="model-row">
        <span class="model-label">当前生效：</span>
        <el-tag :type="modelState === 'READY' ? 'success' : 'info'" size="small">
          {{ tokenizerLabel }}
        </el-tag>
        <span class="model-label" style="margin-left: 20px">完整版状态：</span>
        <el-tag :type="stateTagType" size="small">{{ stateLabel }}</el-tag>
        <el-button
          class="download-btn"
          size="small"
          :loading="downloading"
          :disabled="downloading"
          @click="downloadModel"
        >
          下载完整版语言模型
        </el-button>
      </div>
      <p class="model-note">
        默认使用 <strong>portable</strong>（纯 Java、词典内置、完全离线），演示与断网环境均可用。
        完整版数据包约 500MB，下载后下次启动自动启用；下载失败不影响任何功能。
      </p>
      <p v-if="modelError" class="model-error">最近一次下载失败：{{ modelError }}</p>
    </el-card>

    <el-card>
      <template #header>电影数据</template>
      <el-table :data="movies" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="150" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="commentCount" label="评论数" width="100" />
        <el-table-column label="好评率" width="100">
          <template #default="{ row }">
            {{ (row.positiveRate * 100).toFixed(1) }}%
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { movieApi, adminApi } from '../../api'

const router = useRouter()
const movies = ref([])
const loading = ref(false)
const rerunning = ref(false)

// 分词模型状态
const modelState = ref('NOT_DOWNLOADED')
const activeTokenizer = ref('hanlp-portable')
const downloading = ref(false)
const modelError = ref(null)

const stateLabel = computed(() => ({
  NOT_DOWNLOADED: '未下载',
  DOWNLOADING: '下载中',
  READY: '已就绪',
  FAILED: '下载失败'
}[modelState.value] || '未知'))

const stateTagType = computed(() => ({
  NOT_DOWNLOADED: 'info',
  DOWNLOADING: 'warning',
  READY: 'success',
  FAILED: 'danger'
}[modelState.value] || 'info'))

const tokenizerLabel = computed(() =>
  activeTokenizer.value === 'hanlp-full' ? '完整版 (hanlp-full)' : 'portable (离线可用)')

const loadMovies = async () => {
  loading.value = true
  try {
    const { data } = await movieApi.list({ sort: 'commentCount' })
    movies.value = data.movies || []
  } catch (e) {
    console.error('加载电影失败', e)
  } finally {
    loading.value = false
  }
}

const loadModelStatus = async () => {
  try {
    const { data } = await adminApi.modelStatus()
    modelState.value = data.state
    activeTokenizer.value = data.activeTokenizer
    modelError.value = data.error || null
  } catch (e) {
    console.error('加载模型状态失败', e)
  }
}

const downloadModel = async () => {
  downloading.value = true
  try {
    const { data } = await adminApi.downloadModel()
    modelState.value = data.state
    activeTokenizer.value = data.activeTokenizer
    modelError.value = data.error || null
    if (data.started) {
      ElMessage.success(data.message || '已开始下载')
      pollStatus()
    } else {
      ElMessage.info(data.message || '当前状态不允许下载')
      downloading.value = false
    }
  } catch (e) {
    ElMessage.error('下载请求失败：' + (e.response?.data?.error || e.message))
    downloading.value = false
  }
}

// 下载是异步的，轮询状态直到就绪或失败
const pollStatus = () => {
  const timer = setInterval(async () => {
    try {
      const { data } = await adminApi.modelStatus()
      modelState.value = data.state
      activeTokenizer.value = data.activeTokenizer
      modelError.value = data.error || null

      if (data.state === 'READY') {
        clearInterval(timer)
        downloading.value = false
        ElMessage.success('完整版模型已就绪，下次启动自动启用')
      } else if (data.state === 'FAILED') {
        clearInterval(timer)
        downloading.value = false
        ElMessage.warning('下载失败，系统继续使用 portable 分词')
      }
    } catch (e) {
      clearInterval(timer)
      downloading.value = false
    }
  }, 3000)

  // 保险：最多轮询 20 分钟
  setTimeout(() => { clearInterval(timer); downloading.value = false }, 20 * 60 * 1000)
}

const rerunAll = async () => {
  rerunning.value = true
  try {
    const { data } = await adminApi.rerunAll()
    ElMessage.success(`重跑完成，共 ${data.count} 条评论`)
  } catch (e) {
    ElMessage.error('重跑失败：' + (e.response?.data?.error || e.message))
  } finally {
    rerunning.value = false
  }
}

const viewDetail = (id) => router.push(`/movie/${id}`)

onMounted(() => {
  loadMovies()
  loadModelStatus()
})
</script>

<style scoped>
.data-page { padding: 20px; }
.actions { margin-bottom: 20px; }
.hint { margin-left: 12px; color: #909399; font-size: 13px; }
.model-card { margin-bottom: 20px; }
.model-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.model-label { color: #606266; font-size: 13px; }
.download-btn { margin-left: 20px; }
.model-note { margin: 12px 0 0; color: #909399; font-size: 13px; line-height: 1.6; }
.model-error { margin: 8px 0 0; color: #f56c6c; font-size: 13px; }
</style>
