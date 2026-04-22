<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAppStore } from '../stores/app';
import { useMessage } from '../useMessage';
import { API_BASE_URL, API_ENDPOINTS } from '@shared/constants/api';
import { truncate } from '@shared/utils/formatter';

const store = useAppStore();
const emit = defineEmits<{
  back: [];
  exportComplete: [];
}>();

// 状态
const isExporting = ref(false);
const isDownloading = ref(false);
const { message, showMessage } = useMessage();
const exportResult = ref<{
  type: 'feishu' | 'excel';
  url: string;
  fileId?: string;
  expiresAt?: string;
} | null>(null);

// 预览前3条
const previewPapers = computed(() => store.selectedPapers.slice(0, 3));

// 检查是否为演示模式
async function isDemoMode(): Promise<boolean> {
  const result = await chrome.storage.local.get('accessToken');
  return result.accessToken === 'demo-token';
}

// 导出到飞书
async function exportToFeishu() {
  isExporting.value = true;
  exportResult.value = null;

  try {
    // 演示模式：模拟导出
    if (await isDemoMode()) {
      await new Promise(resolve => setTimeout(resolve, 1000));
      exportResult.value = {
        type: 'feishu',
        url: 'https://docs.feishu.cn/docx/demo-paperpilot-export'
      };
      return;
    }

    const response = await chrome.runtime.sendMessage({
      type: 'EXPORT_FEISHU',
      payload: { papers: store.selectedPapers }
    });

    if (response.success) {
      exportResult.value = {
        type: 'feishu',
        url: response.data.docUrl
      };
    } else {
      showMessage(response.error || '导出失败', 'error');
    }
  } catch (error) {
    showMessage('导出失败，请检查网络连接', 'error');
  } finally {
    isExporting.value = false;
  }
}

// 导出到 Excel
async function exportToExcel() {
  isExporting.value = true;
  exportResult.value = null;

  try {
    // 演示模式：模拟导出
    if (await isDemoMode()) {
      await new Promise(resolve => setTimeout(resolve, 1000));
      const expireDate = new Date();
      expireDate.setDate(expireDate.getDate() + 7);
      exportResult.value = {
        type: 'excel',
        url: 'https://example.com/demo-paperpilot-export.xlsx',
        expiresAt: expireDate.toISOString()
      };
      return;
    }

    const response = await chrome.runtime.sendMessage({
      type: 'EXPORT_EXCEL',
      payload: { papers: store.selectedPapers }
    });

    if (response.success) {
      // 保存 fileId 用于下载
      exportResult.value = {
        type: 'excel',
        url: '', // 本地存储模式不需要URL
        fileId: response.data.fileId,
        expiresAt: response.data.expiresAt
      };
    } else {
      showMessage(response.error || '导出失败', 'error');
    }
  } catch (error) {
    showMessage('导出失败，请检查网络连接', 'error');
  } finally {
    isExporting.value = false;
  }
}

// 打开链接或下载文件
async function openOrDownload() {
  if (!exportResult.value) return;

  if (exportResult.value.type === 'feishu') {
    // 飞书文档直接打开链接
    if (exportResult.value.url) {
      chrome.tabs.create({ url: exportResult.value.url });
    }
  } else if (exportResult.value.type === 'excel') {
    // Excel 使用 fileId 下载
    await downloadExcelFile();
  }
}

// 下载 Excel 文件
async function downloadExcelFile() {
  if (!exportResult.value?.fileId) return;

  isDownloading.value = true;
  try {
    const tokenResult = await chrome.storage.local.get('accessToken');
    const accessToken = tokenResult.accessToken;
    const downloadUrl = `${API_BASE_URL}${API_ENDPOINTS.EXPORT.DOWNLOAD(exportResult.value.fileId)}`;
    const response = await fetch(downloadUrl, {
      method: 'GET',
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {})
      }
    });

    if (response.status === 401) {
      await chrome.storage.local.remove(['accessToken', 'refreshToken']);
      throw new Error('登录已过期，请重新登录');
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ message: '下载失败' }));
      throw new Error(errorData.message || '下载失败');
    }

    const blob = await response.blob();
    const fileName = getDownloadFileName(
      response.headers,
      exportResult.value.fileId
    );

    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Download failed:', error);
    showMessage(error instanceof Error ? error.message : '下载失败，请重试', 'error');
  } finally {
    isDownloading.value = false;
  }
}

function getDownloadFileName(headers: Headers, fileId: string): string {
  const encodedFileName = headers.get('x-file-name');
  if (encodedFileName) {
    return decodeURIComponent(encodedFileName);
  }

  const contentDisposition = headers.get('content-disposition');
  if (contentDisposition) {
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
      return decodeURIComponent(utf8Match[1]);
    }

    const plainMatch = contentDisposition.match(/filename="?([^"]+)"?/i);
    if (plainMatch?.[1]) {
      return plainMatch[1];
    }
  }

  const fileNameFromFileId = fileId.split('/').pop();
  return fileNameFromFileId || `paperpilot_export_${Date.now()}.xlsx`;
}

// 复制链接
async function copyLink(url: string) {
  try {
    await navigator.clipboard.writeText(url);
    showMessage('链接已复制到剪贴板', 'success');
  } catch {
    showMessage('复制失败，请手动复制', 'error');
  }
}

// 完成
function finish() {
  exportResult.value = null;
  emit('exportComplete');
}

// 格式化过期时间
function formatExpires(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}
</script>

<template>
  <div class="flex flex-col h-full p-4 overflow-y-auto scrollbar-thin">
    <h2 class="text-lg font-semibold text-gray-800 mb-4">导出文献</h2>

    <!-- 消息提示 -->
    <div
      v-if="message"
      class="mb-4 p-3 rounded-lg border text-sm"
      :class="message.type === 'success' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-red-50 text-red-700 border-red-200'"
    >
      {{ message.text }}
    </div>

    <!-- 导出结果 -->
    <div
      v-if="exportResult"
      class="flex-1 flex flex-col"
    >
      <div class="flex-1">
        <div
          class="p-4 rounded-lg mb-4"
          :class="exportResult.type === 'feishu' ? 'bg-blue-50' : 'bg-green-50'"
        >
          <div class="flex items-center gap-3 mb-3">
            <div
              class="w-12 h-12 rounded-full flex items-center justify-center text-2xl"
              :class="exportResult.type === 'feishu' ? 'bg-blue-100' : 'bg-green-100'"
            >
              {{ exportResult.type === 'feishu' ? '📄' : '📊' }}
            </div>
            <div>
              <h3 class="font-medium text-gray-800">
                {{ exportResult.type === 'feishu' ? '飞书文档' : 'Excel 文件' }}
              </h3>
              <p class="text-sm text-gray-500">
                共 {{ store.selectedCount }} 篇文献
              </p>
            </div>
          </div>

          <div class="space-y-2">
            <button
              @click="openOrDownload"
              :disabled="isDownloading"
              class="w-full btn-primary"
            >
              <span v-if="isDownloading">下载中...</span>
              <span v-else>
                {{ exportResult.type === 'feishu' ? '打开飞书文档' : '下载 Excel' }}
              </span>
            </button>

            <button
              v-if="exportResult.type === 'feishu' && exportResult.url"
              @click="copyLink(exportResult.url)"
              class="w-full btn-secondary"
            >
              复制链接
            </button>
          </div>

          <p
            v-if="exportResult.expiresAt"
            class="mt-3 text-xs text-center text-gray-500"
          >
            链接将于 {{ formatExpires(exportResult.expiresAt) }} 过期
          </p>
        </div>
      </div>

      <div class="mt-auto pt-4 border-t border-gray-200">
        <button
          @click="finish"
          class="w-full btn-secondary"
        >
          完成
        </button>
      </div>
    </div>

    <!-- 导出选项 -->
    <template v-else>
      <!-- 预览 -->
      <div class="mb-4">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-medium text-gray-700">预览</span>
          <span class="text-xs text-gray-500">
            共 {{ store.selectedCount }} 篇
          </span>
        </div>

        <div class="space-y-2">
          <div
            v-for="(paper, index) in previewPapers"
            :key="paper.doi || paper.title"
            class="p-3 bg-white rounded-lg border border-gray-200"
          >
            <div class="flex items-start gap-2">
              <span class="text-xs text-gray-400">{{ index + 1 }}</span>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-gray-800 truncate">
                  {{ truncate(paper.title, 50) }}
                </p>
                <p
                  v-if="paper.aiSummary"
                  class="text-xs text-gray-500 mt-1 line-clamp-2"
                >
                  {{ paper.aiSummary }}
                </p>
                <p v-else class="text-xs text-gray-400 mt-1">
                  暂无 AI 分析结果
                </p>
              </div>
            </div>
          </div>

          <p
            v-if="store.selectedCount > 3"
            class="text-xs text-center text-gray-400"
          >
            还有 {{ store.selectedCount - 3 }} 篇...
          </p>
        </div>
      </div>

      <!-- 导出按钮 -->
      <div class="flex-1">
        <div class="grid grid-cols-2 gap-3">
          <button
            @click="exportToFeishu"
            :disabled="isExporting"
            class="flex flex-col items-center p-4 bg-white border border-gray-200 rounded-xl hover:border-blue-400 hover:bg-blue-50 transition-colors"
          >
            <span class="text-3xl mb-2">📄</span>
            <span class="font-medium text-gray-800">飞书文档</span>
            <span class="text-xs text-gray-500 mt-1">在线协作</span>
          </button>

          <button
            @click="exportToExcel"
            :disabled="isExporting"
            class="flex flex-col items-center p-4 bg-white border border-gray-200 rounded-xl hover:border-green-400 hover:bg-green-50 transition-colors"
          >
            <span class="text-3xl mb-2">📊</span>
            <span class="font-medium text-gray-800">Excel</span>
            <span class="text-xs text-gray-500 mt-1">7天有效</span>
          </button>
        </div>
      </div>

      <!-- 底部操作 -->
      <div class="mt-auto pt-4 border-t border-gray-200">
        <button
          @click="$emit('back')"
          :disabled="isExporting"
          class="w-full btn-secondary"
        >
          ← 返回
        </button>
      </div>
    </template>
  </div>
</template>
