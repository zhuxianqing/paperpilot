<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import type { AnalysisPaperResult, AnalysisTaskSummary } from '@shared/types/task';

const props = defineProps<{
  taskNo: string;
}>();

const task = ref<AnalysisTaskSummary | null>(null);
const papers = ref<AnalysisPaperResult[]>([]);
const isLoading = ref(false);
const activePaper = ref<AnalysisPaperResult | null>(null);

let pollTimer: ReturnType<typeof setInterval> | null = null;
let isRefreshing = false;

const progressPercentage = computed(() => {
  if (!task.value?.totalCount) {
    return 0;
  }
  return Math.round((task.value.processedCount / task.value.totalCount) * 100);
});

onMounted(() => {
  void refreshTaskData();
});

onUnmounted(() => {
  stopPolling();
});

watch(() => props.taskNo, () => {
  stopPolling();
  task.value = null;
  papers.value = [];
  activePaper.value = null;
  void refreshTaskData();
});

async function refreshTaskData() {
  if (!props.taskNo || isRefreshing) {
    return;
  }

  isRefreshing = true;
  isLoading.value = true;

  try {
    await Promise.all([loadTask(), loadPapers()]);
    syncPolling();
  } finally {
    isRefreshing = false;
    isLoading.value = false;
  }
}

async function loadTask() {
  const response = await chrome.runtime.sendMessage({
    type: 'GET_ANALYSIS_TASK',
    payload: { taskNo: props.taskNo }
  });

  if (response.success) {
    task.value = response.data || null;
  }
}

async function loadPapers() {
  const currentPaperId = activePaper.value?.id;

  const response = await chrome.runtime.sendMessage({
    type: 'GET_ANALYSIS_TASK_PAPERS',
    payload: { taskNo: props.taskNo }
  });

  if (response.success) {
    papers.value = response.data || [];
    activePaper.value = papers.value.find(paper => paper.id === currentPaperId) || papers.value[0] || null;
  }
}

function syncPolling() {
  if (task.value && ['pending', 'processing'].includes(task.value.status)) {
    if (!pollTimer) {
      pollTimer = setInterval(() => {
        void refreshTaskData();
      }, 3000);
    }
    return;
  }

  stopPolling();
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function taskStatusLabel(status?: AnalysisTaskSummary['status']) {
  switch (status) {
    case 'pending': return '待处理';
    case 'processing': return '处理中';
    case 'completed': return '已完成';
    case 'partial_failed': return '部分失败';
    case 'failed': return '失败';
    default: return status || '-';
  }
}

function taskStatusClass(status?: AnalysisTaskSummary['status']) {
  switch (status) {
    case 'completed': return 'bg-green-100 text-green-700';
    case 'processing': return 'bg-blue-100 text-blue-700';
    case 'partial_failed': return 'bg-yellow-100 text-yellow-700';
    case 'failed': return 'bg-red-100 text-red-700';
    default: return 'bg-gray-100 text-gray-700';
  }
}

function paperStatusLabel(status: AnalysisPaperResult['status']) {
  switch (status) {
    case 'pending': return '待处理';
    case 'processing': return '处理中';
    case 'completed': return '已完成';
    case 'failed': return '失败';
    default: return status;
  }
}

function paperStatusClass(status: AnalysisPaperResult['status']) {
  switch (status) {
    case 'completed': return 'bg-green-100 text-green-700';
    case 'processing': return 'bg-blue-100 text-blue-700';
    case 'failed': return 'bg-red-100 text-red-700';
    default: return 'bg-gray-100 text-gray-700';
  }
}

function resultSourceLabel(source?: AnalysisPaperResult['resultSource']) {
  switch (source) {
    case 'new': return '新分析';
    case 'reused': return '复用历史';
    case 'reanalyze': return '重新分析';
    default: return '-';
  }
}

function resultSourceClass(source?: AnalysisPaperResult['resultSource']) {
  switch (source) {
    case 'new': return 'bg-blue-100 text-blue-700';
    case 'reused': return 'bg-purple-100 text-purple-700';
    case 'reanalyze': return 'bg-orange-100 text-orange-700';
    default: return 'bg-gray-100 text-gray-700';
  }
}

function displayTime(value?: string) {
  return value || '-';
}
</script>

<template>
  <div class="space-y-6">
    <div class="card">
      <div class="p-6 border-b border-gray-200 flex items-start justify-between gap-4">
        <div>
          <h2 class="section-title">任务详情</h2>
          <p class="text-sm text-gray-500 mt-1">任务号：{{ props.taskNo }}</p>
        </div>
        <span v-if="task" class="badge" :class="taskStatusClass(task.status)">
          {{ taskStatusLabel(task.status) }}
        </span>
      </div>

      <div v-if="isLoading && !task" class="p-8 text-center text-gray-500">加载中...</div>
      <div v-else-if="task" class="p-6 space-y-5">
        <div>
          <div class="w-full bg-gray-200 rounded-full h-2 mb-2 overflow-hidden">
            <div class="bg-primary-600 h-2 rounded-full transition-all" :style="{ width: `${progressPercentage}%` }"></div>
          </div>
          <div class="text-sm text-gray-600 flex items-center justify-between">
            <span>进度：{{ task.processedCount }}/{{ task.totalCount }}</span>
            <span>成功 {{ task.successCount }} / 失败 {{ task.failedCount }}</span>
          </div>
        </div>

        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 text-sm">
          <div class="rounded-lg bg-gray-50 p-4">
            <p class="text-xs text-gray-500 mb-1">AI 来源</p>
            <p class="text-gray-800">{{ task.useUserConfig ? '我的 API Key' : '系统 AI' }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 p-4">
            <p class="text-xs text-gray-500 mb-1">Provider</p>
            <p class="text-gray-800">{{ task.provider || '-' }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 p-4">
            <p class="text-xs text-gray-500 mb-1">Model</p>
            <p class="text-gray-800 break-all">{{ task.model || '-' }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 p-4">
            <p class="text-xs text-gray-500 mb-1">额度消耗</p>
            <p class="text-gray-800">{{ task.quotaConsumed ?? 0 }}</p>
          </div>
        </div>

        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 text-sm">
          <div>
            <p class="text-xs text-gray-500 mb-1">创建时间</p>
            <p class="text-gray-800">{{ displayTime(task.createdAt) }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-500 mb-1">开始时间</p>
            <p class="text-gray-800">{{ displayTime(task.startedAt) }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-500 mb-1">完成时间</p>
            <p class="text-gray-800">{{ displayTime(task.completedAt) }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-500 mb-1">更新时间</p>
            <p class="text-gray-800">{{ displayTime(task.updatedAt) }}</p>
          </div>
        </div>

        <div v-if="task.errorMessage" class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {{ task.errorMessage }}
        </div>
      </div>
    </div>

    <div class="grid grid-cols-[minmax(0,1fr)_360px] gap-6">
      <div class="card">
        <div class="p-6 border-b border-gray-200">
          <h2 class="section-title">任务结果</h2>
        </div>
        <div v-if="isLoading && papers.length === 0" class="p-8 text-center text-gray-500">加载中...</div>
        <div v-else-if="papers.length === 0" class="p-8 text-center text-gray-500">暂无结果</div>
        <div v-else class="divide-y divide-gray-200 max-h-[720px] overflow-y-auto scrollbar-thin">
          <button
            v-for="paper in papers"
            :key="paper.id"
            class="w-full text-left p-5 hover:bg-gray-50 transition-colors"
            :class="activePaper?.id === paper.id ? 'bg-primary-50' : ''"
            @click="activePaper = paper"
          >
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <p class="font-medium text-gray-800 truncate">{{ paper.title }}</p>
                <p v-if="paper.titleCn" class="text-sm text-gray-500 mt-1 truncate">{{ paper.titleCn }}</p>
                <div class="mt-2 text-xs text-gray-500 flex gap-3 flex-wrap">
                  <span>年份 {{ paper.publishYear || '-' }}</span>
                  <span>被引 {{ paper.citations || 0 }}</span>
                  <span v-if="paper.paperDoi" class="truncate">DOI {{ paper.paperDoi }}</span>
                </div>
              </div>
              <div class="flex flex-col items-end gap-2">
                <span class="badge" :class="paperStatusClass(paper.status)">
                  {{ paperStatusLabel(paper.status) }}
                </span>
                <span v-if="paper.resultSource" class="badge" :class="resultSourceClass(paper.resultSource)">
                  {{ resultSourceLabel(paper.resultSource) }}
                </span>
              </div>
            </div>
          </button>
        </div>
      </div>

      <div class="card h-fit sticky top-6">
        <div class="p-6 border-b border-gray-200">
          <h2 class="section-title">论文详情</h2>
        </div>
        <div v-if="!activePaper" class="p-8 text-center text-gray-500">请选择一篇论文</div>
        <div v-else class="p-6 space-y-4 text-sm">
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="text-xs text-gray-500 mb-1">状态</p>
              <div class="flex items-center gap-2">
                <span class="badge" :class="paperStatusClass(activePaper.status)">
                  {{ paperStatusLabel(activePaper.status) }}
                </span>
                <span v-if="activePaper.resultSource" class="badge" :class="resultSourceClass(activePaper.resultSource)">
                  {{ resultSourceLabel(activePaper.resultSource) }}
                </span>
              </div>
            </div>
            <div class="text-right">
              <p class="text-xs text-gray-500 mb-1">分析完成</p>
              <p class="text-gray-700">{{ displayTime(activePaper.analyzedAt) }}</p>
            </div>
          </div>

          <div>
            <p class="text-xs text-gray-500 mb-1">原标题</p>
            <p class="text-gray-800">{{ activePaper.title }}</p>
          </div>
          <div v-if="activePaper.titleCn">
            <p class="text-xs text-gray-500 mb-1">标题中文翻译</p>
            <p class="text-gray-800">{{ activePaper.titleCn }}</p>
          </div>
          <div v-if="activePaper.abstractText">
            <p class="text-xs text-gray-500 mb-1">原摘要</p>
            <p class="text-gray-700 leading-6">{{ activePaper.abstractText }}</p>
          </div>
          <div v-if="activePaper.abstractCn">
            <p class="text-xs text-gray-500 mb-1">摘要中文翻译</p>
            <p class="text-gray-700 leading-6">{{ activePaper.abstractCn }}</p>
          </div>
          <div v-if="activePaper.keywordsZh">
            <p class="text-xs text-gray-500 mb-1">关键词</p>
            <p class="text-gray-700">{{ activePaper.keywordsZh }}</p>
          </div>
          <div v-if="activePaper.summaryZh">
            <p class="text-xs text-gray-500 mb-1">AI总结</p>
            <p class="text-gray-700 leading-6">{{ activePaper.summaryZh }}</p>
          </div>
          <div v-if="activePaper.methodologyZh">
            <p class="text-xs text-gray-500 mb-1">研究方法</p>
            <p class="text-gray-700 leading-6">{{ activePaper.methodologyZh }}</p>
          </div>
          <div v-if="activePaper.conclusionZh">
            <p class="text-xs text-gray-500 mb-1">研究结论</p>
            <p class="text-gray-700 leading-6">{{ activePaper.conclusionZh }}</p>
          </div>
          <div v-if="activePaper.researchFindingsZh">
            <p class="text-xs text-gray-500 mb-1">研究成果</p>
            <p class="text-gray-700 leading-6">{{ activePaper.researchFindingsZh }}</p>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <p class="text-xs text-gray-500 mb-1">年份</p>
              <p class="text-gray-800">{{ activePaper.publishYear || '-' }}</p>
            </div>
            <div>
              <p class="text-xs text-gray-500 mb-1">被引次数</p>
              <p class="text-gray-800">{{ activePaper.citations || 0 }}</p>
            </div>
          </div>

          <div v-if="activePaper.authors">
            <p class="text-xs text-gray-500 mb-1">作者</p>
            <p class="text-gray-700">{{ activePaper.authors }}</p>
          </div>
          <div v-if="activePaper.journal">
            <p class="text-xs text-gray-500 mb-1">期刊</p>
            <p class="text-gray-700">{{ activePaper.journal }}</p>
          </div>
          <div v-if="activePaper.paperDoi">
            <p class="text-xs text-gray-500 mb-1">DOI</p>
            <p class="text-gray-700 break-all">{{ activePaper.paperDoi }}</p>
          </div>
          <div v-if="activePaper.errorMessage" class="rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">
            {{ activePaper.errorMessage }}
          </div>
          <div v-if="activePaper.sourceUrl">
            <p class="text-xs text-gray-500 mb-1">原文链接</p>
            <a :href="activePaper.sourceUrl" target="_blank" class="text-primary-600 hover:text-primary-700 break-all">{{ activePaper.sourceUrl }}</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
