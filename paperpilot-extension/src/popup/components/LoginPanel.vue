<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { useMessage } from '../useMessage';

const emit = defineEmits<{
  loginSuccess: [];
}>();

const isLogin = ref(true);
const isLoading = ref(false);
const error = ref('');
const { message, showMessage } = useMessage();

// 表单数据
const email = ref('');
const password = ref('');
const confirmPassword = ref('');
const code = ref('');
const nickname = ref('');
const countdown = ref(0);
const codeSentAt = ref<number | null>(null);

// 表单草稿持久化 - 防止弹窗关闭后丢失已填写的注册信息
const DRAFT_KEY = 'registerFormDraft';

onMounted(async () => {
  try {
    const result = await chrome.storage.local.get(DRAFT_KEY);
    const draft = result[DRAFT_KEY];
    if (draft) {
      isLogin.value = draft.isLogin ?? true;
      email.value = draft.email ?? '';
      nickname.value = draft.nickname ?? '';
      password.value = draft.isLogin ? '' : (draft.password ?? '');
      code.value = draft.code ?? '';
      // 恢复验证码倒计时
      if (draft.codeSentAt) {
        const elapsed = Math.floor((Date.now() - draft.codeSentAt) / 1000);
        const remaining = 60 - elapsed;
        if (remaining > 0) {
          codeSentAt.value = draft.codeSentAt;
          countdown.value = remaining;
          runCountdownTimer();
        }
      }
    }
  } catch {
    // 忽略恢复错误
  }
});

watch([isLogin, email, nickname, password, code], saveDraft);

async function saveDraft() {
  try {
    await chrome.storage.local.set({
      [DRAFT_KEY]: {
        isLogin: isLogin.value,
        email: email.value,
        nickname: nickname.value,
        password: isLogin.value ? '' : password.value,
        code: code.value,
        codeSentAt: codeSentAt.value,
      }
    });
  } catch {
    // 忽略保存错误
  }
}

async function clearDraft() {
  try {
    await chrome.storage.local.remove(DRAFT_KEY);
  } catch {
    // 忽略
  }
}

// 发送验证码
async function sendCode() {
  if (!email.value || !isValidEmail(email.value)) {
    error.value = '请输入有效的邮箱地址';
    return;
  }

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'SEND_CODE',
      payload: { email: email.value }
    });

    if (response.success) {
      startCountdown();
      error.value = '';
      showMessage('验证码发送成功', 'success');
    } else {
      error.value = response.error || '发送验证码失败';
    }
  } catch (err) {
    error.value = '发送验证码失败';
  }
}

function startCountdown() {
  countdown.value = 60;
  codeSentAt.value = Date.now();
  runCountdownTimer();
  saveDraft();
}

function runCountdownTimer() {
  const timer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearInterval(timer);
    }
  }, 1000);
}

// 验证邮箱格式
function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

// 登录
async function handleLogin() {
  if (!validateForm()) return;

  isLoading.value = true;
  error.value = '';

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'LOGIN',
      payload: {
        email: email.value,
        password: password.value
      }
    });

    if (response.success) {
      clearDraft();
      emit('loginSuccess');
    } else {
      error.value = response.error || '登录失败';
    }
  } catch (err) {
    error.value = '登录失败，请检查网络连接';
  } finally {
    isLoading.value = false;
  }
}

// 注册
async function handleRegister() {
  if (!validateForm(true)) return;

  isLoading.value = true;
  error.value = '';

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'REGISTER',
      payload: {
        email: email.value,
        password: password.value,
        code: code.value,
        nickname: nickname.value
      }
    });

    if (response.success) {
      clearDraft();
      emit('loginSuccess');
    } else {
      error.value = response.error || '注册失败';
    }
  } catch (err) {
    error.value = '注册失败，请检查网络连接';
  } finally {
    isLoading.value = false;
  }
}

// 验证表单
function validateForm(isRegister = false): boolean {
  if (!email.value || !isValidEmail(email.value)) {
    error.value = '请输入有效的邮箱地址';
    return false;
  }

  if (!password.value || password.value.length < 6) {
    error.value = '密码长度至少为6位';
    return false;
  }

  if (isRegister) {
    if (password.value !== confirmPassword.value) {
      error.value = '两次输入的密码不一致';
      return false;
    }

    if (!code.value) {
      error.value = '请输入验证码';
      return false;
    }
  }

  return true;
}

// 切换登录/注册
function toggleMode() {
  isLogin.value = !isLogin.value;
  error.value = '';
}

// 演示模式：跳过登录，使用本地 Mock 数据
async function enterDemoMode() {
  isLoading.value = true;
  error.value = '';

  try {
    // 创建演示用户
    const demoUser = {
      id: 1,
      email: 'demo@example.com',
      nickname: '演示用户',
      quotaBalance: 999,
      isVip: true,
      vipExpireAt: '2099-12-31'
    };

    // 保存到本地存储
    await chrome.storage.local.set({
      accessToken: 'demo-token',
      user: demoUser
    });

    // 加载演示数据
    await loadDemoPapers();

    // 通知父组件登录成功
    clearDraft();
    emit('loginSuccess');
  } catch (err) {
    error.value = '进入演示模式失败';
    console.error('Demo mode error:', err);
  } finally {
    isLoading.value = false;
  }
}

// 加载演示文献数据
async function loadDemoPapers() {
  const demoPapers = [
    {
      id: 1,
      title: 'A Survey on Large Language Models: Applications, Challenges, and Future Directions',
      authors: ['Y. Liu', 'Y. Liu', 'J. Zhang', 'et al.'],
      abstract: 'Large language models have revolutionized natural language processing...',
      journal: 'IEEE Transactions on Neural Networks',
      publishYear: 2024,
      impactFactor: 8.2,
      quartile: 'Q1',
      citations: 245,
      doi: '10.1109/TNNLS.2024.1234567',
      sourceUrl: 'https://www.webofscience.com/wos/woscc/full-record/WOS:001234567890',
      selected: true,
      aiSummary: '这篇综述全面概述了大语言模型的发展历程、核心技术和应用场景，并指出了当前面临的主要挑战和未来的研究方向。',
      methodology: '文献综述、对比分析',
      conclusion: '大语言模型在多个领域展现出巨大潜力，但仍需解决幻觉、安全性和可解释性等问题。',
      aiKeywords: ['Large Language Model', 'NLP', 'Deep Learning', 'Transformer']
    },
    {
      id: 2,
      title: 'Deep Learning for Computer Vision: A Comprehensive Review',
      authors: ['K. He', 'R. Girshick', 'P. Dollár'],
      journal: 'IEEE Transactions on Pattern Analysis',
      publishYear: 2023,
      impactFactor: 20.8,
      quartile: 'Q1',
      citations: 1892,
      doi: '10.1109/TPAMI.2023.9876543',
      sourceUrl: 'https://www.webofscience.com/wos/woscc/full-record/WOS:009876543210',
      selected: true,
      aiSummary: '系统回顾了深度学习在计算机视觉领域的关键技术突破，包括卷积神经网络、注意力机制和自监督学习等方向。',
      methodology: '系统性文献综述、实验对比',
      conclusion: '深度学习已经显著推动了计算机视觉的发展，未来多模态学习和自监督学习将是重要方向。',
      aiKeywords: ['Computer Vision', 'Deep Learning', 'CNN', 'Self-supervised Learning']
    },
    {
      id: 3,
      title: 'Transformer Architecture: The New Paradigm for Natural Language Processing',
      authors: ['A. Vaswani', 'N. Shazeer', 'N. Parmar'],
      journal: 'Advances in Neural Information Processing',
      publishYear: 2017,
      impactFactor: 11.2,
      quartile: 'Q1',
      citations: 45231,
      doi: '10.5555/3295222.3295349',
      sourceUrl: 'https://www.webofscience.com/wos/woscc/full-record/WOS:000112233445',
      selected: true
    },
    {
      id: 4,
      title: 'Reinforcement Learning with Human Feedback: Theory and Applications',
      authors: ['P. Christiano', 'J. Leike', 'T. Brown'],
      journal: 'Journal of Machine Learning Research',
      publishYear: 2023,
      impactFactor: 4.3,
      quartile: 'Q2',
      citations: 567,
      doi: '10.5555/3455716.3455718',
      sourceUrl: 'https://www.webofscience.com/wos/woscc/full-record/WOS:000554433221',
      selected: false
    },
    {
      id: 5,
      title: 'Generative Adversarial Networks for Medical Image Synthesis',
      authors: ['H. Kaur', 'S. Kumar', 'R. Singh'],
      journal: 'Medical Image Analysis',
      publishYear: 2022,
      impactFactor: 13.0,
      quartile: 'Q1',
      citations: 328,
      doi: '10.1016/j.media.2022.123456',
      sourceUrl: 'https://www.webofscience.com/wos/woscc/full-record/WOS:000667788990',
      selected: true
    }
  ];

  await chrome.storage.local.set({
    cachedPapers: demoPapers,
    papersUpdatedAt: Date.now()
  });
}
</script>

<template>
  <div class="flex flex-col items-center justify-center h-[500px] px-6">
    <!-- Logo -->
    <div class="w-16 h-16 bg-primary-600 rounded-2xl flex items-center justify-center text-white text-3xl font-bold mb-4">
      P
    </div>
    <h1 class="text-2xl font-bold text-gray-800 mb-1">PaperPilot</h1>
    <p class="text-sm text-gray-500 mb-8">学术文献助手</p>

    <!-- 错误提示 -->
    <div
      v-if="message"
      class="w-full p-3 mb-4 rounded-lg border text-sm"
      :class="message.type === 'success' ? 'bg-green-50 text-green-700 border-green-200' : 'bg-red-50 text-red-700 border-red-200'"
    >
      {{ message.text }}
    </div>

    <div
      v-if="error"
      class="w-full p-3 mb-4 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm"
    >
      {{ error }}
    </div>

    <!-- 表单 -->
    <form class="w-full space-y-4" @submit.prevent="isLogin ? handleLogin() : handleRegister()">
      <!-- 邮箱 -->
      <div>
        <input
          v-model="email"
          type="email"
          placeholder="邮箱"
          class="input"
          :disabled="isLoading"
        />
      </div>

      <!-- 昵称（仅注册） -->
      <div v-if="!isLogin">
        <input
          v-model="nickname"
          type="text"
          placeholder="昵称（可选）"
          class="input"
          :disabled="isLoading"
        />
      </div>

      <!-- 密码 -->
      <div>
        <input
          v-model="password"
          type="password"
          placeholder="密码"
          class="input"
          :disabled="isLoading"
        />
      </div>

      <!-- 确认密码（仅注册） -->
      <div v-if="!isLogin">
        <input
          v-model="confirmPassword"
          type="password"
          placeholder="确认密码"
          class="input"
          :disabled="isLoading"
        />
      </div>

      <!-- 验证码（仅注册） -->
      <div v-if="!isLogin" class="flex gap-2">
        <input
          v-model="code"
          type="text"
          placeholder="验证码"
          class="input flex-1"
          :disabled="isLoading"
        />
        <button
          type="button"
          @click="sendCode"
          :disabled="countdown > 0 || isLoading"
          class="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed transition-colors whitespace-nowrap"
        >
          {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
        </button>
      </div>

      <!-- 提交按钮 -->
      <button
        type="submit"
        :disabled="isLoading"
        class="w-full btn-primary py-3"
      >
        <span v-if="isLoading" class="flex items-center justify-center gap-2">
          <svg class="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          {{ isLogin ? '登录中...' : '注册中...' }}
        </span>
        <span v-else>{{ isLogin ? '登录' : '注册' }}</span>
      </button>
    </form>

    <!-- 切换登录/注册 -->
    <p class="mt-4 text-sm text-gray-600">
      {{ isLogin ? '还没有账号？' : '已有账号？' }}
      <button
        @click="toggleMode"
        class="text-primary-600 hover:text-primary-700 font-medium"
      >
        {{ isLogin ? '立即注册' : '立即登录' }}
      </button>
    </p>

    <!-- 演示模式入口 -->
    <div class="mt-6 pt-6 border-t border-gray-200">
      <button
        @click="enterDemoMode"
        :disabled="isLoading"
        class="w-full py-2 px-4 bg-gray-100 hover:bg-gray-200 text-gray-600 rounded-lg text-sm transition-colors"
      >
        🚀 演示模式（跳过登录）
      </button>
      <p class="mt-2 text-xs text-gray-400 text-center">
        用于测试插件功能，数据仅保存在本地
      </p>
    </div>
  </div>
</template>
