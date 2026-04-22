<script setup lang="ts">
import type { QuotaInfo } from '@shared/types/user';

defineProps<{
  user: { email: string; nickname: string } | null;
  quota: QuotaInfo;
}>();

defineEmits<{
  openSettings: [];
  logout: [];
}>();
</script>

<template>
  <header class="flex items-center justify-between px-4 py-3 bg-white border-b border-gray-200 flex-shrink-0">
    <div class="flex items-center gap-2">
      <div class="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center text-white font-bold">
        P
      </div>
      <div>
        <h1 class="text-sm font-semibold text-gray-800">PaperPilot</h1>
        <p class="text-xs text-gray-500 truncate max-w-[120px]">{{ user?.nickname || user?.email }}</p>
      </div>
    </div>

    <div class="flex items-center gap-2">
      <!-- 额度显示 -->
      <div class="flex items-center gap-1 text-xs">
        <span
          v-if="quota.balance > 0"
          class="px-2 py-1 bg-primary-100 text-primary-700 rounded-full font-medium"
        >
          剩余 {{ quota.balance }} 次
        </span>
        <span
          v-else
          class="px-2 py-1 bg-green-100 text-green-700 rounded-full font-medium"
        >
          免费 {{ 3 - quota.freeQuotaUsed }}/3
        </span>
      </div>

      <!-- 设置按钮 -->
      <button
        @click="$emit('openSettings')"
        class="p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
        title="设置"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </button>

      <!-- 退出按钮 -->
      <button
        @click="$emit('logout')"
        class="p-1.5 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
        title="退出登录"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
        </svg>
      </button>
    </div>
  </header>
</template>
