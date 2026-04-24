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

  </div>
</template>
