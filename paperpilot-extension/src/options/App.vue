<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { UserAIConfig } from '@shared/types/user';
import { AI_PROVIDERS, DEFAULT_MODELS } from '@shared/constants/api';

// 状态
const configs = ref<UserAIConfig[]>([]);
const isLoading = ref(false);
const message = ref<{ type: 'success' | 'error'; text: string } | null>(null);

// 编辑模式
const editingConfig = ref<UserAIConfig | null>(null);
const isEditing = ref(false);

// 表单
const form = ref<UserAIConfig>({
  provider: 'deepseek',
  apiKey: '',
  model: 'deepseek-chat',
  baseUrl: '',
  isDefault: false
});

// 测试状态
const isTesting = ref(false);
const testResult = ref<{ success: boolean; message: string } | null>(null);

onMounted(() => {
  loadConfigs();
});

// 加载配置
async function loadConfigs() {
  isLoading.value = true;
  try {
    const response = await chrome.runtime.sendMessage({ type: 'GET_AI_CONFIGS' });
    if (response.success) {
      configs.value = response.data || [];
    }
  } catch (error) {
    showMessage('加载配置失败', 'error');
  } finally {
    isLoading.value = false;
  }
}

// 更新默认模型
function updateDefaultModel() {
  form.value.model = DEFAULT_MODELS[form.value.provider] || '';
}

// 测试配置
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

// 保存配置
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

// 删除配置
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

// 开始编辑
function startEdit(config?: UserAIConfig) {
  if (config) {
    editingConfig.value = config;
    form.value = { ...config };
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

// 取消编辑
function cancelEdit() {
  isEditing.value = false;
  editingConfig.value = null;
  testResult.value = null;
}

// 显示消息
function showMessage(text: string, type: 'success' | 'error') {
  message.value = { text, type };
  setTimeout(() => message.value = null, 3000);
}

// 获取提供商名称
function getProviderLabel(provider: string): string {
  const p = AI_PROVIDERS.find(p => p.value === provider);
  return p?.label || provider;
}

// 掩码 API Key
function maskApiKey(key: string): string {
  if (key.length <= 8) return '****';
  return key.substring(0, 4) + '****' + key.substring(key.length - 4);
}
</script>

<template>
  <div class="min-h-screen py-8 px-4">
    <div class="max-w-3xl mx-auto">
      <!-- 头部 -->
      <div class="flex items-center gap-3 mb-8">
        <div class="w-12 h-12 bg-primary-600 rounded-xl flex items-center justify-center text-white text-2xl font-bold">
          P
        </div>
        <div>
          <h1 class="text-2xl font-bold text-gray-800">PaperPilot 设置</h1>
          <p class="text-gray-500">管理你的 AI 配置</p>
        </div>
      </div>

      <!-- 消息提示 -->
      <div
        v-if="message"
        class="mb-6 p-4 rounded-lg"
        :class="message.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'"
      >
        {{ message.text }}
      </div>

      <!-- 配置列表 -->
      <div v-if="!isEditing" class="card">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <h2 class="section-title">AI 配置</h2>
          <button @click="startEdit()" class="btn-primary text-sm">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            添加配置
          </button>
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
          <div
            v-for="config in configs"
            :key="config.provider"
            class="p-6 hover:bg-gray-50 transition-colors"
          >
            <div class="flex items-start justify-between">
              <div>
                <div class="flex items-center gap-2 mb-1">
                  <h3 class="font-medium text-gray-800">{{ getProviderLabel(config.provider) }}</h3>
                  <span v-if="config.isDefault" class="badge bg-primary-100 text-primary-700">
                    默认
                  </span>
                </div>
                <p class="text-sm text-gray-500 mb-1">模型: {{ config.model }}</p>
                <p class="text-sm text-gray-400">API Key: {{ maskApiKey(config.apiKey) }}</p>
                <p v-if="config.baseUrl" class="text-sm text-gray-400 mt-1">
                  Base URL: {{ config.baseUrl }}
                </p>
              </div>
              <div class="flex items-center gap-2">
                <button
                  @click="startEdit(config)"
                  class="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors"
                  title="编辑"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button
                  @click="deleteConfig(config.provider)"
                  class="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  title="删除"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 编辑表单 -->
      <div v-else class="card">
        <div class="p-6 border-b border-gray-200">
          <h2 class="section-title">
            {{ editingConfig ? '编辑配置' : '添加配置' }}
          </h2>
        </div>

        <div class="p-6 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">AI 提供商</label>
            <select
              v-model="form.provider"
              @change="updateDefaultModel"
              class="select"
              :disabled="!!editingConfig"
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
            <label class="block text-sm font-medium text-gray-700 mb-1">模型</label>
            <input
              v-model="form.model"
              type="text"
              :placeholder="DEFAULT_MODELS[form.provider]"
              class="input"
            />
            <p class="mt-1 text-xs text-gray-500">
              例如: gpt-4, deepseek-chat, claude-3-sonnet
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">API Key</label>
            <input
              v-model="form.apiKey"
              type="password"
              placeholder="sk-..."
              class="input"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">
              Base URL（可选）
            </label>
            <input
              v-model="form.baseUrl"
              type="text"
              placeholder="https://api.example.com/v1"
              class="input"
            />
            <p class="mt-1 text-xs text-gray-500">
              使用第三方中转服务时填写，留空使用官方地址
            </p>
          </div>

          <div class="flex items-center gap-2">
            <input
              id="isDefault"
              v-model="form.isDefault"
              type="checkbox"
              class="w-4 h-4 text-primary-600 rounded focus:ring-primary-500"
            />
            <label for="isDefault" class="text-sm text-gray-700">
              设为默认配置
            </label>
          </div>

          <!-- 测试结果 -->
          <div
            v-if="testResult"
            class="p-3 rounded-lg"
            :class="testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'"
          >
            {{ testResult.message }}
          </div>

          <!-- 操作按钮 -->
          <div class="flex gap-3 pt-4">
            <button @click="cancelEdit" class="btn-secondary flex-1">
              取消
            </button>
            <button
              @click="testConfig"
              :disabled="isTesting || !form.apiKey"
              class="btn-secondary flex-1"
            >
              <span v-if="isTesting">测试中...</span>
              <span v-else>测试连接</span>
            </button>
            <button
              @click="saveConfig"
              :disabled="isLoading || !form.apiKey"
              class="btn-primary flex-1"
            >
              <span v-if="isLoading">保存中...</span>
              <span v-else>保存</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 帮助信息 -->
      <div class="mt-8 p-6 bg-blue-50 rounded-xl border border-blue-100">
        <h3 class="font-medium text-blue-800 mb-2">关于 BYOK（Bring Your Own Key）</h3>
        <p class="text-sm text-blue-700 mb-2">
          BYOK 功能允许你使用自己的 AI API Key 进行文献分析，这样可以：
        </p>
        <ul class="text-sm text-blue-700 list-disc list-inside space-y-1">
          <li>免费使用 AI 分析功能（不消耗系统额度）</li>
          <li>保护隐私，文献数据不经过第三方服务器</li>
          <li>支持 OpenAI、DeepSeek、Claude 等多种模型</li>
        </ul>
      </div>
    </div>
  </div>
</template>
