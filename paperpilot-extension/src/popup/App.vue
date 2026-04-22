<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useAppStore } from './stores/app';
import { useMessage } from './useMessage';
import { getAccessToken, getUser } from '@shared/utils/storage';
import LoginPanel from './components/LoginPanel.vue';
import PaperSelector from './components/PaperSelector.vue';
import AIConfigPanel from './components/AIConfigPanel.vue';
import ExportPanel from './components/ExportPanel.vue';
import UserHeader from './components/UserHeader.vue';

const store = useAppStore();

// 当前视图
const currentView = ref<'login' | 'papers' | 'ai-config' | 'export'>('login');
const isInitializing = ref(true);
const { message, showMessage } = useMessage();

// 初始化
onMounted(async () => {
  await initialize();
});

async function initialize() {
  isInitializing.value = true;

  try {
    // 检查登录状态
    const token = await getAccessToken();
    const user = await getUser();

    if (token && user) {
      store.setUser(user);
      await store.refreshQuota();

      // 仅在当前页面与论文来源页面匹配时加载缓存文献
      const shouldLoad = await shouldLoadPapers();
      if (shouldLoad) {
        await store.loadPapersFromStorage();
      } else {
        store.setPapers([]);
      }

      currentView.value = 'papers';
    } else {
      currentView.value = 'login';
    }
  } catch (error) {
    console.error('Initialization error:', error);
    currentView.value = 'login';
  } finally {
    isInitializing.value = false;
  }
}

/**
 * 判断当前 active tab 是否匹配缓存论文的来源页面
 * 通过 background script 查询当前 tab URL（popup 中 tabs.query 不可靠）
 */
async function shouldLoadPapers(): Promise<boolean> {
  try {
    const result = await chrome.storage.local.get('cachedPapersHost');
    const savedHost = result.cachedPapersHost as string | undefined;

    // 无来源记录（旧数据或从未拦截过论文）时允许加载
    if (!savedHost) return true;

    const response = await chrome.runtime.sendMessage({ type: 'GET_CURRENT_TAB_HOST' });
    if (!response.success || !response.data) return false;

    const currentUrl = response.data as string;
    return currentUrl.includes(savedHost);
  } catch {
    return true;
  }
}

// 登录成功
function onLoginSuccess() {
  currentView.value = 'papers';
  store.refreshUser();
  store.refreshQuota();
}

// 退出登录
async function logout() {
  try {
    await chrome.runtime.sendMessage({ type: 'LOGOUT' });
    store.setUser(null);
    store.clearPapers();
    currentView.value = 'login';
  } catch (error) {
    console.error('Logout error:', error);
  }
}

// 前往 AI 配置
function goToAIConfig() {
  if (store.selectedCount === 0) {
    showMessage('请至少选择一篇文献', 'error');
    return;
  }
  currentView.value = 'ai-config';
}

// 前往导出
function goToExport() {
  currentView.value = 'export';
}

// 返回文献选择
function backToPapers() {
  currentView.value = 'papers';
}

// 返回 AI 配置
function backToAIConfig() {
  currentView.value = 'ai-config';
}

// 打开设置页面
function openSettings() {
  chrome.runtime.openOptionsPage();
}
</script>

<template>
  <div class="flex flex-col h-full bg-gray-50">
    <!-- 初始化加载 -->
    <div
      v-if="isInitializing"
      class="flex items-center justify-center h-[500px]"
    >
      <div class="text-center">
        <div class="w-8 h-8 border-2 border-primary-500 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
        <p class="text-gray-500">加载中...</p>
      </div>
    </div>

    <!-- 主内容 -->
    <template v-else>
      <!-- 登录视图 -->
      <LoginPanel
        v-if="currentView === 'login'"
        @login-success="onLoginSuccess"
      />

      <!-- 主界面（登录后） -->
      <div
        v-else
        class="flex flex-col h-full"
      >
        <!-- 用户头部 -->
        <UserHeader
          :user="store.user"
          :quota="store.quota"
          @open-settings="openSettings"
          @logout="logout"
        />

        <!-- 消息提示 -->
        <div
          v-if="message"
          class="mx-4 mt-3 p-3 rounded-lg border text-sm flex-shrink-0"
          :class="message.type === 'success' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-red-50 text-red-700 border-red-200'"
        >
          {{ message.text }}
        </div>

        <!-- 错误提示 -->
        <div
          v-if="store.error"
          class="mx-4 mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm flex-shrink-0"
        >
          {{ store.error }}
          <button
            @click="store.setError(null)"
            class="float-right text-red-400 hover:text-red-600"
          >
            ✕
          </button>
        </div>

        <!-- 视图切换 -->
        <div class="flex-1 overflow-hidden">
          <PaperSelector
            v-if="currentView === 'papers'"
            @next="goToAIConfig"
          />

          <AIConfigPanel
            v-else-if="currentView === 'ai-config'"
            @back="backToPapers"
            @analyze-complete="goToExport"
          />

          <ExportPanel
            v-else-if="currentView === 'export'"
            @back="backToAIConfig"
            @export-complete="backToPapers"
          />
        </div>
      </div>
    </template>
  </div>
</template>
