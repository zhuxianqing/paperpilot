<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useAppStore } from '../stores/app';
import { useMessage } from '../useMessage';
import { AI_PROVIDERS, DEFAULT_MODELS } from '@shared/constants/api';
import type { UserAIConfig } from '@shared/types/user';
import type { Paper } from '@shared/types/paper';

const store = useAppStore();
const emit = defineEmits<{
  back: [];
  analyzeComplete: [];
}>();

// 状态
const aiSource = ref<'system' | 'user'>('system');
const savedConfigs = ref<UserAIConfig[]>([]);
const selectedConfig = ref<UserAIConfig | null>(null);

// 新建配置
const showAddConfig = ref(false);
const newConfig = ref<UserAIConfig>({
  provider: 'deepseek',
  apiKey: '',
  model: 'deepseek-chat',
  isDefault: false
});

// 加载状态
const isAnalyzing = ref(false);
const isTesting = ref(false);
const isSaving = ref(false);
const testResult = ref<{ success: boolean; message: string } | null>(null);
const { message, showMessage } = useMessage();

onMounted(async () => {
  await loadAIConfigs();
});

// 加载已保存的 AI 配置
async function loadAIConfigs() {
  try {
    const response = await chrome.runtime.sendMessage({ type: 'GET_AI_CONFIGS' });
    if (response.success) {
      savedConfigs.value = response.data || [];
      // 默认选中第一个
      if (savedConfigs.value.length > 0) {
        selectedConfig.value = savedConfigs.value[0];
      }
    }
  } catch (error) {
    console.error('Failed to load AI configs:', error);
  }
}

// 更新默认模型
function updateDefaultModel() {
  newConfig.value.model = DEFAULT_MODELS[newConfig.value.provider] || '';
}

// 测试配置
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
  } catch (error) {
    testResult.value = { success: false, message: '测试失败' };
  } finally {
    isTesting.value = false;
  }
}

// 保存配置
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
      // 重置表单
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

// 模拟 AI 分析（演示模式用）
function simulateAIAnalysis(papers: Paper[]): Paper[] {
  const summaries = [
    '本研究提出了一种新颖的深度学习方法，在多个基准数据集上取得了最先进的性能。实验结果表明该方法在准确性和效率方面都有显著提升。',
    '通过大规模实验验证，该研究系统地分析了影响模型性能的关键因素，并提出了一套完整的优化策略，为后续研究提供了重要参考。',
    '这项工作填补了该领域的研究空白，提出的理论框架具有良好的泛化能力，可以推广到相关的应用场景中。',
    '研究采用了严格的实验设计，通过消融实验验证了各个组件的有效性，结论可靠且具有实际应用价值。'
  ];

  const methodologies = [
    '深度神经网络、对比学习、自监督预训练',
    '系统性文献综述、实验对比、统计分析',
    '理论分析、算法设计、大规模实验验证',
    '多模态融合、注意力机制、迁移学习'
  ];

  const conclusions = [
    '实验结果验证了所提出方法的有效性，相比现有方法在多个指标上都有显著提升。',
    '研究发现了影响模型性能的关键因素，为后续优化提供了明确方向。',
    '所提出的框架具有良好的泛化能力，可以应用于多种实际场景。',
    '理论分析和实验结果一致，证明了方法的可行性和有效性。'
  ];

  return papers.map(paper => {
    if (paper.aiSummary) return paper; // 已有分析结果

    const randomIndex = Math.floor(Math.random() * summaries.length);
    return {
      ...paper,
      aiSummary: summaries[randomIndex],
      methodology: methodologies[randomIndex],
      conclusion: conclusions[randomIndex],
      aiKeywords: [
        'Deep Learning',
        'Machine Learning',
        'AI',
        paper.journal?.split(' ')[0] || 'Research'
      ]
    };
  });
}

// 检查是否为演示模式
async function isDemoMode(): Promise<boolean> {
  const result = await chrome.storage.local.get('accessToken');
  return result.accessToken === 'demo-token';
}

// 开始分析
async function startAnalyze() {
  if (aiSource.value === 'user' && !selectedConfig.value) {
    showMessage('请选择或添加一个 AI 配置', 'error');
    return;
  }

  isAnalyzing.value = true;

  try {
    // 检查是否是演示模式
    if (await isDemoMode()) {
      // 模拟 AI 分析
      await new Promise(resolve => setTimeout(resolve, 1500)); // 模拟延迟
      const analyzedPapers = simulateAIAnalysis(store.selectedPapers);
      store.updateAnalyzedPapers(analyzedPapers);
      emit('analyzeComplete');
      return;
    }

    // 正常模式：调用后端 API
    const response = await chrome.runtime.sendMessage({
      type: 'ANALYZE_PAPERS',
      payload: {
        papers: store.selectedPapers,
        useUserConfig: aiSource.value === 'user',
        config: aiSource.value === 'user' ? selectedConfig.value : undefined
      }
    });

    if (response.success) {
      // 更新文献数据
      store.updateAnalyzedPapers(response.data);
      // 刷新额度
      await store.refreshQuota();
      // 进入导出页面
      emit('analyzeComplete');
    } else {
      showMessage(response.error || '分析失败', 'error');
    }
  } catch (error) {
    showMessage('分析失败，请检查网络连接', 'error');
  } finally {
    isAnalyzing.value = false;
  }
}

// 获取提供商名称
function getProviderLabel(provider: string): string {
  const p = AI_PROVIDERS.find(p => p.value === provider);
  return p?.label || provider;
}
</script>

<template>
  <div class="flex flex-col h-full p-4 overflow-y-auto scrollbar-thin">
    <h2 class="text-lg font-semibold text-gray-800 mb-4">AI 分析配置</h2>

    <!-- 消息提示 -->
    <div
      v-if="message"
      class="mb-4 p-3 rounded-lg border text-sm"
      :class="message.type === 'success' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-red-50 text-red-700 border-red-200'"
    >
      {{ message.text }}
    </div>

    <!-- AI 来源选择 -->
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

    <!-- 用户配置选择 -->
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

      <!-- 添加配置表单 -->
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

          <!-- 测试结果 -->
          <div
            v-if="testResult"
            class="p-2 rounded text-sm"
            :class="testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'"
          >
            {{ testResult.message }}
          </div>

          <!-- 操作按钮 -->
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

    <!-- 底部操作 -->
    <div class="mt-auto pt-4 border-t border-gray-200">
      <div class="flex gap-3">
        <button
          @click="$emit('back')"
          class="btn-secondary flex-1"
        >
          ← 返回
        </button>
        <button
          @click="startAnalyze"
          :disabled="isAnalyzing || (aiSource === 'user' && !selectedConfig)"
          class="btn-primary flex-1"
        >
          <span v-if="isAnalyzing" class="flex items-center justify-center gap-2">
            <svg class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            分析中...
          </span>
          <span v-else>开始 AI 分析</span>
        </button>
      </div>

      <p class="mt-2 text-xs text-center text-gray-400">
        分析 {{ store.selectedCount }} 篇文献
      </p>
    </div>
  </div>
</template>
