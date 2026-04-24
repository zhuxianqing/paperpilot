<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useAppStore } from '../stores/app';
import { useMessage } from '../useMessage';
import { AI_PROVIDERS, DEFAULT_MODELS } from '@shared/constants/api';
import type { UserAIConfig, UserAIConfigSummary } from '@shared/types/user';
import type { AnalysisTaskSummary } from '@shared/types/task';

const emit = defineEmits<{
  back: [];
}>();

const store = useAppStore();
const aiSource = ref<'system' | 'user'>('system');
const savedConfigs = ref<UserAIConfigSummary[]>([]);
const selectedConfig = ref<UserAIConfigSummary | null>(null);
const showAddConfig = ref(false);
const newConfig = ref<UserAIConfig>({
  provider: 'deepseek',
  apiKey: '',
  model: 'deepseek-chat',
  isDefault: false
});

const isSubmitting = ref(false);
const isTesting = ref(false);
const isSaving = ref(false);
const submittedTask = ref<AnalysisTaskSummary | null>(null);
const testResult = ref<{ success: boolean; message: string } | null>(null);
const { message, showMessage } = useMessage();

onMounted(async () => {
  await loadAIConfigs();
});

async function loadAIConfigs() {
  try {
    const response = await chrome.runtime.sendMessage({ type: 'GET_AI_CONFIGS' });
    if (response.success) {
      savedConfigs.value = response.data || [];
      if (savedConfigs.value.length > 0) {
        selectedConfig.value = savedConfigs.value[0];
      }
    }
  } catch (error) {
    console.error('Failed to load AI configs:', error);
  }
}

function updateDefaultModel() {
  newConfig.value.model = DEFAULT_MODELS[newConfig.value.provider] || '';
}

async function testConfig() {
  if (!newConfig.value.apiKey) {
    testResult.value = { success: false, message: '请输入 API Key' };
    return;
  }

  isTesting.value = true;
  testResult.value = null;

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'TEST_AI_CONFIG',
      payload: { config: newConfig.value }
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
  if (!newConfig.value.apiKey) {
    return;
  }

  isSaving.value = true;

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'SAVE_AI_CONFIG',
      payload: { config: newConfig.value }
    });

    if (response.success) {
      await loadAIConfigs();
      showAddConfig.value = false;
      newConfig.value = {
        provider: 'deepseek',
        apiKey: '',
        model: 'deepseek-chat',
        isDefault: false
      };
    }
  } catch (error) {
    console.error('Failed to save config:', error);
  } finally {
    isSaving.value = false;
  }
}

async function startAnalyze() {
  if (aiSource.value === 'user' && !selectedConfig.value) {
    showMessage('请选择或添加一个 AI 配置', 'error');
    return;
  }

  isSubmitting.value = true;

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'SUBMIT_ANALYSIS_TASK',
      payload: {
        papers: store.selectedPapers,
        useUserConfig: aiSource.value === 'user',
        config: aiSource.value === 'user' ? {
          provider: selectedConfig.value?.provider,
          model: selectedConfig.value?.model,
          baseUrl: selectedConfig.value?.baseUrl,
          apiKey: ''
        } : undefined
      }
    });

    if (response.success) {
      submittedTask.value = response.data;
      showMessage('分析任务已提交', 'success');
      await store.refreshQuota();
    } else {
      showMessage(response.error || '任务提交失败', 'error');
    }
  } catch {
    showMessage('任务提交失败，请检查网络连接', 'error');
  } finally {
    isSubmitting.value = false;
  }
}

function getProviderLabel(provider: string): string {
  const p = AI_PROVIDERS.find(p => p.value === provider);
  return p?.label || provider;
}

function openOptionsPage() {
  chrome.runtime.openOptionsPage();
}

function backToPapers() {
  emit('back');
}
</script>

<template>
  <div class="flex flex-col h-full p-4 overflow-y-auto scrollbar-thin">
    <h2 class="text-lg font-semibold text-gray-800 mb-4">AI 分析配置</h2>

    <div
      v-if="message"
      class="mb-4 p-3 rounded-lg border text-sm"
      :class="message.type === 'success' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-red-50 text-red-700 border-red-200'"
    >
      {{ message.text }}
    </div>

    <div v-if="submittedTask" class="flex-1 flex flex-col justify-center">
      <div class="panel text-center">
        <div class="w-14 h-14 rounded-full bg-green-100 text-green-600 flex items-center justify-center mx-auto mb-4 text-2xl">
          ✓
        </div>
        <h3 class="text-lg font-semibold text-gray-800">任务已提交</h3>
        <p class="text-sm text-gray-500 mt-2">
          任务号：{{ submittedTask.taskNo }}
        </p>
        <p class="text-sm text-gray-500 mt-1">
          已提交 {{ store.selectedCount }} 篇文献，popup 不再驻留等待分析完成。
        </p>
        <div class="mt-6 flex gap-3">
          <button class="btn-primary flex-1" @click="openOptionsPage">打开设置页查看进度</button>
          <button class="btn-secondary flex-1" @click="backToPapers">返回论文列表</button>
        </div>
      </div>
    </div>

    <template v-else>
      <div class="space-y-3 mb-6">
        <label
          class="radio-label"
          :class="{ 'border-primary-500 bg-primary-50': aiSource === 'system' }"
        >
          <input
            type="radio"
            v-model="aiSource"
            value="system"
            class="w-4 h-4 text-primary-600"
          />
          <div class="flex-1">
            <div class="flex items-center justify-between">
              <span class="font-medium text-gray-800">使用系统 AI</span>
              <span
                v-if="store.quota.balance > 0"
                class="badge quota"
              >
                剩余 {{ store.quota.balance }} 次
              </span>
              <span
                v-else
                class="badge free"
              >
                免费 {{ 3 - store.quota.freeQuotaUsed }}/3
              </span>
            </div>
            <p class="text-xs text-gray-500 mt-1">
              使用我们的 AI 服务，按次计费
            </p>
          </div>
        </label>

        <label
          class="radio-label"
          :class="{ 'border-primary-500 bg-primary-50': aiSource === 'user' }"
        >
          <input
            type="radio"
            v-model="aiSource"
            value="user"
            class="w-4 h-4 text-primary-600"
          />
          <div class="flex-1">
            <div class="flex items-center justify-between">
              <span class="font-medium text-gray-800">使用我的 API Key</span>
              <span class="badge free">免费</span>
            </div>
            <p class="text-xs text-gray-500 mt-1">
              使用你自己的 OpenAI/DeepSeek/Claude API Key
            </p>
          </div>
        </label>
      </div>

      <div v-if="aiSource === 'user'" class="mb-4">
        <div v-if="!showAddConfig">
          <div v-if="savedConfigs.length > 0" class="mb-3">
            <label class="block text-sm font-medium text-gray-700 mb-2">
              选择配置
            </label>
            <select
              v-model="selectedConfig"
              class="select"
            >
              <option
                v-for="config in savedConfigs"
                :key="config.provider"
                :value="config"
              >
                {{ getProviderLabel(config.provider) }} - {{ config.model }}
              </option>
            </select>
          </div>

          <div v-else class="p-4 bg-yellow-50 rounded-lg mb-3">
            <p class="text-sm text-yellow-800">
              暂无保存的配置，请先添加
            </p>
          </div>

          <button
            @click="showAddConfig = true"
            class="btn-link"
          >
            + 添加新配置
          </button>
        </div>

        <div v-else class="panel">
          <h3 class="text-sm font-medium text-gray-800 mb-3">添加 API 配置</h3>

          <div class="space-y-3">
            <div>
              <label class="block text-xs text-gray-600 mb-1">AI 提供商</label>
              <select
                v-model="newConfig.provider"
                @change="updateDefaultModel"
                class="select"
              >
                <option
                  v-for="provider in AI_PROVIDERS"
                  :key="provider.value"
                  :value="provider.value"
                >
                  {{ provider.label }}
                </option>
              </select>
            </div>

            <div>
              <label class="block text-xs text-gray-600 mb-1">模型</label>
              <input
                v-model="newConfig.model"
                type="text"
                :placeholder="DEFAULT_MODELS[newConfig.provider]"
                class="input"
              />
            </div>

            <div>
              <label class="block text-xs text-gray-600 mb-1">API Key</label>
              <input
                v-model="newConfig.apiKey"
                type="password"
                placeholder="sk-..."
                class="input"
              />
            </div>

            <div>
              <label class="block text-xs text-gray-600 mb-1">
                Base URL（可选）
              </label>
              <input
                v-model="newConfig.baseUrl"
                type="text"
                placeholder="https://api.example.com/v1"
                class="input"
              />
            </div>

            <div
              v-if="testResult"
              class="p-2 rounded text-sm"
              :class="testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'"
            >
              {{ testResult.message }}
            </div>

            <div class="flex gap-2 pt-2">
              <button
                @click="testConfig"
                :disabled="isTesting || !newConfig.apiKey"
                class="flex-1 btn-secondary"
              >
                {{ isTesting ? '测试中...' : '测试连接' }}
              </button>
              <button
                @click="saveConfig"
                :disabled="isSaving || !newConfig.apiKey"
                class="flex-1 btn-primary"
              >
                {{ isSaving ? '保存中...' : '保存' }}
              </button>
            </div>

            <button
              @click="showAddConfig = false"
              class="w-full text-sm text-gray-500 hover:text-gray-700"
            >
              取消
            </button>
          </div>
        </div>
      </div>

      <div class="mt-auto pt-4 border-t border-gray-200">
        <div class="flex gap-3">
          <button
            @click="emit('back')"
            class="btn-secondary flex-1"
          >
            ← 返回
          </button>
          <button
            @click="startAnalyze"
            :disabled="isSubmitting || (aiSource === 'user' && !selectedConfig)"
            class="btn-primary flex-1"
          >
            <span v-if="isSubmitting" class="flex items-center justify-center gap-2">
              <svg class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              提交中...
            </span>
            <span v-else>开始 AI 分析</span>
          </button>
        </div>

        <p class="mt-2 text-xs text-center text-gray-400">
          提交 {{ store.selectedCount }} 篇文献进行分析
        </p>
      </div>
    </template>
  </div>
</template>
