<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { AnalysisPaperResult } from '@shared/types/task';

const papers = ref<AnalysisPaperResult[]>([]);
const activePaper = ref<AnalysisPaperResult | null>(null);
const isLoading = ref(false);
const sortBy = ref<'year' | 'citations'>('year');
const order = ref<'asc' | 'desc'>('desc');
const keyword = ref('');
const page = ref(1);
const size = ref(20);
const total = ref(0);

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)));
const canGoPrev = computed(() => page.value > 1);
const canGoNext = computed(() => page.value < totalPages.value);

onMounted(() => {
  void loadHistory();
});

async function loadHistory(resetActivePaper = false) {
  isLoading.value = true;
  try {
    const currentPaperId = activePaper.value?.id;
    const response = await chrome.runtime.sendMessage({
      type: 'LIST_ANALYSIS_HISTORY',
      payload: {
        page: page.value,
        size: size.value,
        sortBy: sortBy.value,
        order: order.value,
        keyword: keyword.value || undefined
      }
    });
    if (response.success) {
      papers.value = response.data?.records || [];
      total.value = response.data?.total || 0;
      activePaper.value = resetActivePaper
        ? (papers.value[0] || null)
        : papers.value.find(paper => paper.id === currentPaperId) || papers.value[0] || null;
    }
  } finally {
    isLoading.value = false;
  }
}

function applySort(nextSortBy: 'year' | 'citations') {
  sortBy.value = nextSortBy;
  order.value = 'desc';
  page.value = 1;
  void loadHistory(true);
}

function toggleOrder() {
  order.value = order.value === 'desc' ? 'asc' : 'desc';
  page.value = 1;
  void loadHistory(true);
}

function search() {
  page.value = 1;
  void loadHistory(true);
}

function goToPage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) {
    return;
  }
  page.value = nextPage;
  void loadHistory(true);
}

function statusLabel(status: AnalysisPaperResult['status']) {
  switch (status) {
    case 'pending': return '待处理';
    case 'processing': return '处理中';
    case 'completed': return '已完成';
    case 'failed': return '失败';
    default: return status;
  }
}

function statusClass(status: AnalysisPaperResult['status']) {
  switch (status) {
    case 'completed': return 'bg-green-100 text-green-700';
    case 'processing': return 'bg-blue-100 text-blue-700';
    case 'failed': return 'bg-red-100 text-red-700';
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
      <div class="p-6 border-b border-gray-200 flex items-center justify-between gap-4 flex-wrap">
        <h2 class="section-title">分析历史</h2>
        <div class="flex items-center gap-3 flex-wrap">
          <input v-model="keyword" class="input w-64" placeholder="搜索标题/DOI" @keyup.enter="search" />
          <button class="btn-secondary text-sm" @click="search">搜索</button>
          <button class="btn-secondary text-sm" @click="applySort('citations')">按被引次数排序</button>
          <button class="btn-secondary text-sm" @click="applySort('year')">按年份排序</button>
          <button class="btn-secondary text-sm" @click="toggleOrder">{{ order === 'desc' ? '当前倒序' : '当前正序' }}</button>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-[minmax(0,1fr)_360px] gap-6">
      <div class="card">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <div>
            <h3 class="section-title">历史列表</h3>
            <p class="text-sm text-gray-500 mt-1">共 {{ total }} 条记录，第 {{ page }}/{{ totalPages }} 页</p>
          </div>
          <div class="flex items-center gap-2">
            <button class="btn-secondary text-sm" :disabled="!canGoPrev" @click="goToPage(page - 1)">上一页</button>
            <button class="btn-secondary text-sm" :disabled="!canGoNext" @click="goToPage(page + 1)">下一页</button>
          </div>
        </div>

        <div v-if="isLoading && papers.length === 0" class="p-8 text-center text-gray-500">加载中...</div>
        <div v-else-if="papers.length === 0" class="p-8 text-center text-gray-500">暂无历史记录</div>
        <div v-else class="divide-y divide-gray-200 max-h-[720px] overflow-y-auto scrollbar-thin">
          <button
            v-for="paper in papers"
            :key="paper.id"
            class="w-full text-left p-6 hover:bg-gray-50 transition-colors"
            :class="activePaper?.id === paper.id ? 'bg-primary-50' : ''"
            @click="activePaper = paper"
          >
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <p class="font-medium text-gray-800">{{ paper.title }}</p>
                <p v-if="paper.titleCn" class="text-sm text-gray-500 mt-1">{{ paper.titleCn }}</p>
                <p v-if="paper.summaryZh" class="text-sm text-gray-700 mt-3 line-clamp-3">{{ paper.summaryZh }}</p>
                <div class="mt-3 text-xs text-gray-500 flex gap-4 flex-wrap">
                  <span>年份 {{ paper.publishYear || '-' }}</span>
                  <span>被引 {{ paper.citations || 0 }}</span>
                  <span>完成 {{ displayTime(paper.analyzedAt) }}</span>
                  <span v-if="paper.paperDoi" class="break-all">DOI {{ paper.paperDoi }}</span>
                </div>
              </div>
              <span class="badge" :class="statusClass(paper.status)">
                {{ statusLabel(paper.status) }}
              </span>
            </div>
          </button>
        </div>
      </div>

      <div class="card h-fit sticky top-6">
        <div class="p-6 border-b border-gray-200">
          <h3 class="section-title">论文详情</h3>
        </div>

        <div v-if="!activePaper" class="p-8 text-center text-gray-500">请选择一篇论文</div>
        <div v-else class="p-6 space-y-4 text-sm">
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="text-xs text-gray-500 mb-1">状态</p>
              <span class="badge" :class="statusClass(activePaper.status)">
                {{ statusLabel(activePaper.status) }}
              </span>
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
