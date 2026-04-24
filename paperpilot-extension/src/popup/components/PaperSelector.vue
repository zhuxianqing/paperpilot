<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAppStore } from '../stores/app';
import { truncate, formatAuthors, formatNumber, getQuartileClass } from '@shared/utils/formatter';
import type { Paper } from '@shared/types/paper';

const store = useAppStore();
defineEmits<{
  next: [];
}>();

const isRefreshing = ref(false);

const totalCount = computed(() => store.papers.length);
const selectedCount = computed(() => store.selectedCount);
const allSelected = computed(() =>
  store.papers.length > 0 && store.papers.every(p => p.selected)
);
const selectedAnalyzedCount = computed(() =>
  store.papers.filter((paper) => paper.selected && paper.isAnalyzed).length
);

async function refreshPapers() {
  isRefreshing.value = true;
  try {
    const shouldLoad = await shouldLoadPapers();
    if (shouldLoad) {
      await store.loadPapersFromStorage();
      await store.lookupAnalysisStatus();
    } else {
      store.setPapers([]);
    }
  } finally {
    isRefreshing.value = false;
  }
}

async function shouldLoadPapers(): Promise<boolean> {
  try {
    const result = await chrome.storage.local.get('cachedPapersHost');
    const savedHost = result.cachedPapersHost as string | undefined;
    if (!savedHost) return true;

    const response = await chrome.runtime.sendMessage({ type: 'GET_CURRENT_TAB_HOST' });
    if (!response.success || !response.data) return false;

    const currentUrl = response.data as string;
    return currentUrl.includes(savedHost);
  } catch {
    return true;
  }
}

function toggleSelectAll() {
  if (allSelected.value) {
    void store.selectNone();
  } else {
    void store.selectAll();
  }
}

function selectQ1Only() {
  void store.selectQ1Only();
}

function toggleSelection(paper: Paper) {
  void store.togglePaperSelection(paper.paperKey || paper.doi);
}

function openSource(url: string) {
  if (url) {
    chrome.tabs.create({ url });
  }
}

function setDecision(paper: Paper, decision: 'reuse' | 'reanalyze') {
  store.setPaperReuseDecision(paper.paperKey || '', decision);
}

function setBatchDecision(decision: 'reuse' | 'reanalyze') {
  void store.applyBatchReuseDecision(decision);
}

function statusLabel(status?: Paper['analysisStatus']) {
  switch (status) {
    case 'completed': return '已分析';
    case 'processing': return '分析中';
    case 'pending': return '待分析';
    case 'failed': return '分析失败';
    default: return '已分析';
  }
}
</script>

<template>
  <div class="flex flex-col h-full p-4">
    <div class="flex items-center justify-between mb-3 flex-shrink-0">
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-600">
          已选择 <span class="font-semibold text-primary-600">{{ selectedCount }}</span> / {{ totalCount }}
        </span>
      </div>
      <div class="flex items-center gap-1">
        <button
          @click="toggleSelectAll"
          class="px-2 py-1 text-xs font-medium text-gray-600 hover:text-primary-600 hover:bg-primary-50 rounded transition-colors"
        >
          {{ allSelected ? '取消全选' : '全选' }}
        </button>
        <button
          @click="selectQ1Only"
          class="px-2 py-1 text-xs font-medium text-green-600 hover:bg-green-50 rounded transition-colors"
        >
          仅选Q1
        </button>
        <button
          @click="refreshPapers"
          :disabled="isRefreshing"
          class="p-1 text-gray-400 hover:text-primary-600 transition-colors"
          title="刷新"
        >
          <svg
            class="w-4 h-4"
            :class="{ 'animate-spin': isRefreshing }"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>
    </div>

    <div
      v-if="selectedAnalyzedCount > 0"
      class="mb-3 p-3 bg-purple-50 border border-purple-200 rounded-lg flex items-center justify-between gap-3"
    >
      <div>
        <p class="text-sm text-purple-800">已选论文中有 {{ selectedAnalyzedCount }} 篇存在历史分析结果</p>
        <p class="text-xs text-purple-600 mt-1">可统一选择复用历史结果，或强制重新分析。</p>
      </div>
      <div class="flex gap-2 shrink-0">
        <button class="btn-secondary text-xs" @click="setBatchDecision('reuse')">统一复用</button>
        <button class="btn-primary text-xs" @click="setBatchDecision('reanalyze')">统一重跑</button>
      </div>
    </div>

    <div class="flex-1 overflow-y-auto scrollbar-thin -mx-4 px-4 min-h-0">
      <div
        v-if="totalCount === 0"
        class="flex flex-col items-center justify-center h-64 text-center"
      >
        <div class="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mb-3">
          <svg class="w-8 h-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        </div>
        <p class="text-gray-500 text-sm mb-1">暂无文献数据</p>
        <p class="text-gray-400 text-xs">请在 WOS 搜索页面进行搜索</p>
      </div>

      <div v-else class="space-y-2">
        <div
          v-for="paper in store.papers"
          :key="paper.paperKey || paper.doi || paper.title"
          class="group p-3 bg-white rounded-lg border border-gray-200 hover:border-primary-300 transition-colors"
          :class="{ 'ring-1 ring-primary-500 border-primary-500': paper.selected }"
        >
          <div class="flex gap-3">
            <div class="pt-0.5">
              <input
                type="checkbox"
                :checked="paper.selected"
                @change="toggleSelection(paper)"
                class="checkbox"
              />
            </div>

            <div class="flex-1 min-w-0">
              <div class="flex items-start justify-between gap-3">
                <h3
                  class="text-sm font-medium text-gray-800 mb-1 cursor-pointer hover:text-primary-600"
                  @click="openSource(paper.sourceUrl)"
                >
                  {{ truncate(paper.title, 80) }}
                </h3>
                <span
                  v-if="paper.isAnalyzed"
                  class="badge bg-purple-100 text-purple-700 shrink-0"
                >
                  AI
                </span>
              </div>

              <p class="text-xs text-gray-500 mb-1.5">
                {{ formatAuthors(paper.authors, 2) || 'Unknown' }}
              </p>

              <div class="flex flex-wrap items-center gap-2 text-xs">
                <span v-if="paper.journal" class="text-gray-600 truncate max-w-[150px]">
                  {{ paper.journal }}
                </span>
                <span
                  v-if="paper.quartile"
                  :class="getQuartileClass(paper.quartile)"
                >
                  {{ paper.quartile }}
                </span>
                <span v-if="paper.publishYear" class="text-gray-500">
                  {{ paper.publishYear }}
                </span>
                <span v-if="paper.citations !== undefined" class="flex items-center gap-0.5 text-gray-500">
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 11l5-5m0 0l5 5m-5-5v12" />
                  </svg>
                  {{ formatNumber(paper.citations) }}
                </span>
                <span v-if="paper.isAnalyzed" class="text-purple-700 bg-purple-50 px-2 py-0.5 rounded-full">
                  {{ statusLabel(paper.analysisStatus) }}
                </span>
              </div>

              <div
                v-if="paper.selected && paper.isAnalyzed"
                class="mt-3 flex items-center gap-2 text-xs"
              >
                <span class="text-gray-500">本次选择：</span>
                <button
                  class="px-2 py-1 rounded border transition-colors"
                  :class="paper.reuseDecision === 'reuse' ? 'border-purple-400 bg-purple-50 text-purple-700' : 'border-gray-200 text-gray-500 hover:border-purple-200'"
                  @click="setDecision(paper, 'reuse')"
                >
                  复用已有分析
                </button>
                <button
                  class="px-2 py-1 rounded border transition-colors"
                  :class="paper.reuseDecision === 'reanalyze' ? 'border-primary-400 bg-primary-50 text-primary-700' : 'border-gray-200 text-gray-500 hover:border-primary-200'"
                  @click="setDecision(paper, 'reanalyze')"
                >
                  重新分析
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="flex-shrink-0 bg-gray-50 pt-4 pb-2 px-2 border-t border-gray-200">
      <button
        @click="$emit('next')"
        :disabled="selectedCount === 0"
        class="w-full btn-primary"
      >
        下一步：配置 AI 分析
        <span class="ml-1">→</span>
      </button>
      <p v-if="selectedCount === 0" class="mt-2 text-xs text-center text-gray-400">
        请至少选择一篇文献
      </p>
    </div>
  </div>
</template>
