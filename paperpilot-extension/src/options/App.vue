<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { UserAIConfig, UserAIConfigSummary } from '@shared/types/user';
import { AI_PROVIDERS, DEFAULT_MODELS } from '@shared/constants/api';
import AnalysisTasksView from './components/AnalysisTasksView.vue';
import AnalysisTaskDetailView from './components/AnalysisTaskDetailView.vue';
import AnalysisHistoryView from './components/AnalysisHistoryView.vue';

type TabKey = 'configs' | 'tasks' | 'history';

const activeTab = ref<TabKey>('tasks');
const selectedTaskNo = ref('');

const configs = ref<UserAIConfigSummary[]>([]);
const isLoading = ref(false);
const message = ref<{ type: 'success' | 'error'; text: string } | null>(null);
const editingConfig = ref<UserAIConfigSummary | null>(null);
const isEditing = ref(false);
const form = ref<UserAIConfig>({
  provider: 'deepseek',
  apiKey: '',
  model: 'deepseek-chat',
  baseUrl: '',
  isDefault: false
});
const isTesting = ref(false);
const testResult = ref<{ success: boolean; message: string } | null>(null);

onMounted(() => {
  void initialize();
});

async function initialize() {
  await Promise.all([
    loadConfigs(),
    restoreSelectedTask()
  ]);
}

async function restoreSelectedTask() {
  try {
    const result = await chrome.storage.local.get('currentAnalysisTaskNo');
    selectedTaskNo.value = result.currentAnalysisTaskNo || '';
  } catch {
    selectedTaskNo.value = '';
  }
}

async function loadConfigs() {
  isLoading.value = true;
  try {
    const response = await chrome.runtime.sendMessage({ type: 'GET_AI_CONFIGS' });
    if (response.success) {
      configs.value = response.data || [];
    }
  } catch {
    showMessage('加载配置失败', 'error');
  } finally {
    isLoading.value = false;
  }
}

function updateDefaultModel() {
  form.value.model = DEFAULT_MODELS[form.value.provider] || '';
}

async function testConfig() {
  if (!form.value.apiKey) {
    testResult.value = { success: false, message: '请输入 API Key' };
    return;
  }
  isTesting.value = true;
  testResult.value = null;
  try {
    const response = await chrome.runtime.sendMessage({
      type: 'TEST_AI_CONFIG',
      payload: { config: form.value }
    });
    testResult.value = response.success
      ? { success: true, message: '连接成功！' }
      : { success: false, message: response.error || '测试失败' };
  } catch {
    testResult.value = { success: false, message: '测试失败' };
  } finally {
    isTesting.value = false;
  }
}

async function saveConfig() {
  if (!form.value.apiKey) {
    showMessage('请输入 API Key', 'error');
    return;
  }
  isLoading.value = true;
  try {
    const response = await chrome.runtime.sendMessage({
      type: 'SAVE_AI_CONFIG',
      payload: { config: form.value }
    });
    if (response.success) {
      showMessage('保存成功', 'success');
      await loadConfigs();
      cancelEdit();
    } else {
      showMessage(response.error || '保存失败', 'error');
    }
  } catch {
    showMessage('保存失败', 'error');
  } finally {
    isLoading.value = false;
  }
}

async function deleteConfig(provider: string) {
  if (!confirm('确定要删除这个配置吗？')) return;
  isLoading.value = true;
  try {
    const response = await chrome.runtime.sendMessage({
      type: 'DELETE_AI_CONFIG',
      payload: { provider }
    });
    if (response.success) {
      showMessage('删除成功', 'success');
      await loadConfigs();
    } else {
      showMessage(response.error || '删除失败', 'error');
    }
  } catch {
    showMessage('删除失败', 'error');
  } finally {
    isLoading.value = false;
  }
}

function startEdit(config?: UserAIConfigSummary) {
  if (config) {
    editingConfig.value = config;
    form.value = {
      provider: config.provider,
      apiKey: '',
      model: config.model,
      baseUrl: config.baseUrl || '',
      isDefault: config.isDefault ?? false
    };
  } else {
    editingConfig.value = null;
    form.value = {
      provider: 'deepseek',
      apiKey: '',
      model: 'deepseek-chat',
      baseUrl: '',
      isDefault: false
    };
  }
  isEditing.value = true;
  testResult.value = null;
}

function cancelEdit() {
  isEditing.value = false;
  editingConfig.value = null;
  testResult.value = null;
}

function showMessage(text: string, type: 'success' | 'error') {
  message.value = { text, type };
  setTimeout(() => (message.value = null), 3000);
}

function getProviderLabel(provider: string): string {
  const p = AI_PROVIDERS.find(item => item.value === provider);
  return p?.label || provider;
}

const tabClass = (tab: TabKey) =>
  activeTab.value === tab
    ? 'bg-primary-600 text-white'
    : 'bg-white text-gray-700 hover:bg-gray-50 border border-gray-200';

async function openTask(taskNo: string) {
  selectedTaskNo.value = taskNo;
  activeTab.value = 'tasks';
  await chrome.storage.local.set({ currentAnalysisTaskNo: taskNo });
}
</script>

<template>
  <div class="min-h-screen py-8 px-4">
    <div class="max-w-6xl mx-auto">
      <div class="flex items-center gap-3 mb-8">
        <div class="w-12 h-12 bg-primary-600 rounded-xl flex items-center justify-center text-white text-2xl font-bold">P</div>
        <div>
          <h1 class="text-2xl font-bold text-gray-800">PaperPilot 设置</h1>
          <p class="text-gray-500">AI 配置、分析任务与历史记录</p>
        </div>
      </div>

      <div v-if="message" class="mb-6 p-4 rounded-lg" :class="message.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'">
        {{ message.text }}
      </div>

      <div class="flex gap-3 mb-6">
        <button class="btn" :class="tabClass('tasks')" @click="activeTab = 'tasks'">分析任务</button>
        <button class="btn" :class="tabClass('history')" @click="activeTab = 'history'">分析历史</button>
        <button class="btn" :class="tabClass('configs')" @click="activeTab = 'configs'">AI 配置</button>
      </div>

      <template v-if="activeTab === 'tasks'">
        <div class="space-y-6">
          <AnalysisTasksView :selected-task-no="selectedTaskNo" @open-task="openTask" />
          <AnalysisTaskDetailView v-if="selectedTaskNo" :task-no="selectedTaskNo" />
        </div>
      </template>

      <template v-else-if="activeTab === 'history'">
        <AnalysisHistoryView />
      </template>

      <template v-else>
        <div v-if="!isEditing" class="card">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="section-title">AI 配置</h2>
            <button @click="startEdit()" class="btn-primary text-sm">添加配置</button>
          </div>

          <div v-if="configs.length === 0" class="p-8 text-center">
            <div class="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg class="w-8 h-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.384-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
              </svg>
            </div>
            <p class="text-gray-500 mb-2">暂无 AI 配置</p>
            <p class="text-sm text-gray-400">添加你自己的 API Key，使用 AI 分析免费</p>
          </div>

          <div v-else class="divide-y divide-gray-200">
            <div v-for="config in configs" :key="config.provider" class="p-6 hover:bg-gray-50 transition-colors">
              <div class="flex items-start justify-between">
                <div>
                  <div class="flex items-center gap-2 mb-1">
                    <h3 class="font-medium text-gray-800">{{ getProviderLabel(config.provider) }}</h3>
                    <span v-if="config.isDefault" class="badge bg-primary-100 text-primary-700">默认</span>
                  </div>
                  <p class="text-sm text-gray-500 mb-1">模型: {{ config.model }}</p>
                  <p class="text-sm text-gray-400">API Key: 已配置</p>
                  <p v-if="config.baseUrl" class="text-sm text-gray-400 mt-1">Base URL: {{ config.baseUrl }}</p>
                </div>
                <div class="flex items-center gap-2">
                  <button @click="startEdit(config)" class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors" title="编辑">编辑</button>
                  <button @click="deleteConfig(config.provider)" class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors" title="删除">删除</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="card">
          <div class="p-6 border-b border-gray-200">
            <h2 class="section-title">{{ editingConfig ? '编辑配置' : '添加配置' }}</h2>
          </div>
          <div class="p-6 space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">AI 提供商</label>
              <select v-model="form.provider" @change="updateDefaultModel" class="select" :disabled="!!editingConfig">
                <option v-for="provider in AI_PROVIDERS" :key="provider.value" :value="provider.value">{{ provider.label }}</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">模型</label>
              <input v-model="form.model" type="text" :placeholder="DEFAULT_MODELS[form.provider]" class="input" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">API Key</label>
              <input v-model="form.apiKey" type="password" placeholder="sk-..." class="input" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Base URL（可选）</label>
              <input v-model="form.baseUrl" type="text" placeholder="https://api.example.com/v1" class="input" />
            </div>
            <div class="flex items-center gap-2">
              <input id="isDefault" v-model="form.isDefault" type="checkbox" class="w-4 h-4 text-primary-600 rounded focus:ring-primary-500" />
              <label for="isDefault" class="text-sm text-gray-700">设为默认配置</label>
            </div>
            <div v-if="testResult" class="p-3 rounded-lg" :class="testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'">
              {{ testResult.message }}
            </div>
            <div class="flex gap-3 pt-4">
              <button @click="cancelEdit" class="btn-secondary flex-1">取消</button>
              <button @click="testConfig" :disabled="isTesting || !form.apiKey" class="btn-secondary flex-1">
                <span v-if="isTesting">测试中...</span>
                <span v-else>测试连接</span>
              </button>
              <button @click="saveConfig" :disabled="isLoading || !form.apiKey" class="btn-primary flex-1">
                <span v-if="isLoading">保存中...</span>
                <span v-else>保存</span>
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
