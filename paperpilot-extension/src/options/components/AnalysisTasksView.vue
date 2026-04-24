<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import type { AnalysisTaskSummary } from '@shared/types/task';

const props = defineProps<{
  selectedTaskNo?: string;
}>();

const tasks = ref<AnalysisTaskSummary[]>([]);
const isLoading = ref(false);
const emit = defineEmits<{
  openTask: [taskNo: string];
}>();

let pollTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  void loadTasks();
  pollTimer = setInterval(() => {
    void loadTasks();
  }, 3000);
});

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});

async function loadTasks() {
  isLoading.value = true;
  try {
    const response = await chrome.runtime.sendMessage({
      type: 'LIST_ANALYSIS_TASKS',
      payload: { page: 1, size: 20 }
    });
    if (response.success) {
      tasks.value = response.data?.records || [];
      if (tasks.value.length > 0) {
        const matchedTask = tasks.value.some(task => task.taskNo === props.selectedTaskNo);
        if (!matchedTask) {
          emit('openTask', tasks.value[0].taskNo);
        }
      }
    }
  } finally {
    isLoading.value = false;
  }
}

function statusLabel(status: AnalysisTaskSummary['status']) {
  switch (status) {
    case 'pending': return '待处理';
    case 'processing': return '处理中';
    case 'completed': return '已完成';
    case 'partial_failed': return '部分失败';
    case 'failed': return '失败';
    default: return status;
  }
}

function statusClass(status: AnalysisTaskSummary['status']) {
  switch (status) {
    case 'completed': return 'bg-green-100 text-green-700';
    case 'processing': return 'bg-blue-100 text-blue-700';
    case 'partial_failed': return 'bg-yellow-100 text-yellow-700';
    case 'failed': return 'bg-red-100 text-red-700';
    default: return 'bg-gray-100 text-gray-700';
  }
}
</script>

<template>
  <div class="card">
    <div class="p-6 border-b border-gray-200 flex items-center justify-between">
      <h2 class="section-title">分析任务</h2>
      <button class="btn-secondary text-sm" @click="loadTasks">刷新</button>
    </div>

    <div v-if="isLoading" class="p-8 text-center text-gray-500">加载中...</div>
    <div v-else-if="tasks.length === 0" class="p-8 text-center text-gray-500">暂无分析任务</div>
    <div v-else class="divide-y divide-gray-200">
      <div
        v-for="task in tasks"
        :key="task.taskNo"
        class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
        :class="task.taskNo === props.selectedTaskNo ? 'bg-primary-50' : ''"
        @click="emit('openTask', task.taskNo)"
      >
        <div class="flex items-start justify-between mb-3">
          <div>
            <p class="font-medium text-gray-800">任务号：{{ task.taskNo }}</p>
            <p class="text-sm text-gray-500 mt-1">{{ task.createdAt }}</p>
          </div>
          <span class="badge" :class="statusClass(task.status)">{{ statusLabel(task.status) }}</span>
        </div>

        <div class="text-sm text-gray-500 mb-3 flex items-center gap-4">
          <span>{{ task.useUserConfig ? '我的 API Key' : '系统 AI' }}</span>
          <span v-if="task.provider">{{ task.provider }}</span>
          <span v-if="task.model">{{ task.model }}</span>
        </div>

        <div class="w-full bg-gray-200 rounded-full h-2 mb-2 overflow-hidden">
          <div
            class="bg-primary-600 h-2 rounded-full transition-all"
            :style="{ width: `${task.totalCount ? Math.round((task.processedCount / task.totalCount) * 100) : 0}%` }"
          ></div>
        </div>

        <div class="text-sm text-gray-600 flex items-center justify-between">
          <span>进度：{{ task.processedCount }}/{{ task.totalCount }}</span>
          <span>成功 {{ task.successCount }} / 失败 {{ task.failedCount }}</span>
        </div>

        <p v-if="task.errorMessage" class="mt-3 text-sm text-red-600 line-clamp-2">
          {{ task.errorMessage }}
        </p>
      </div>
    </div>
  </div>
</template>
