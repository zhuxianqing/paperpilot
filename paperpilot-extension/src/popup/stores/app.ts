/**
 * 全局状态管理
 */

import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Paper } from '@shared/types/paper';
import type { QuotaInfo, User } from '@shared/types/user';
import type { SearchTask } from '@shared/types/task';

export const useAppStore = defineStore('app', () => {
  // ===== State =====
  const user = ref<User | null>(null);
  const papers = ref<Paper[]>([]);
  const currentTask = ref<SearchTask | null>(null);
  const quota = ref<QuotaInfo>({
    balance: 0,
    freeQuotaUsed: 0,
    freeQuotaTotal: 3
  });
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  // ===== Getters =====
  const selectedPapers = computed(() => papers.value.filter(p => p.selected));
  const selectedCount = computed(() => selectedPapers.value.length);
  const hasPapers = computed(() => papers.value.length > 0);
  const hasQuota = computed(() =>
    quota.value.balance > 0 ||
    quota.value.freeQuotaTotal > quota.value.freeQuotaUsed
  );
  const isLoggedIn = computed(() => !!user.value);

  // ===== Actions =====

  /**
   * 设置错误信息
   */
  function setError(message: string | null) {
    error.value = message;
  }

  /**
   * 设置加载状态
   */
  function setLoading(loading: boolean) {
    isLoading.value = loading;
  }

  /**
   * 设置用户信息
   */
  function setUser(userData: User | null) {
    user.value = userData;
  }

  /**
   * 设置额度信息
   */
  function setQuota(quotaData: QuotaInfo) {
    quota.value = quotaData;
  }

  /**
   * 设置文献列表
   */
  function setPapers(papersData: Paper[]) {
    papers.value = papersData.map((p, idx) => ({
      ...p,
      id: p.id || idx + 1,
      selected: p.selected ?? true
    }));
  }

  /**
   * 更新文献列表
   */
  function updatePapers(updatedPapers: Paper[]) {
    papers.value = updatedPapers;
  }

  /**
   * 全选文献
   */
  function selectAll() {
    papers.value = papers.value.map(p => ({ ...p, selected: true }));
  }

  /**
   * 取消全选
   */
  function selectNone() {
    papers.value = papers.value.map(p => ({ ...p, selected: false }));
  }

  /**
   * 仅选择Q1文献
   */
  function selectQ1Only() {
    papers.value = papers.value.map(p => ({
      ...p,
      selected: p.quartile === 'Q1'
    }));
  }

  /**
   * 切换文献选中状态
   */
  function togglePaperSelection(doi: string | undefined) {
    const paper = papers.value.find(p => p.doi === doi);
    if (paper) {
      paper.selected = !paper.selected;
    }
  }

  /**
   * 从存储加载文献
   */
  async function loadPapersFromStorage() {
    try {
      const response = await chrome.runtime.sendMessage({ type: 'LOAD_PAPERS' });
      if (response.success && response.data) {
        setPapers(response.data);
      }
    } catch (err) {
      console.error('Failed to load papers:', err);
    }
  }

  /**
   * 保存文献到存储
   */
  async function savePapersToStorage() {
    try {
      await chrome.runtime.sendMessage({
        type: 'SAVE_PAPERS',
        payload: { papers: papers.value }
      });
    } catch (err) {
      console.error('Failed to save papers:', err);
    }
  }

  /**
   * 更新分析后的文献
   */
  function updateAnalyzedPapers(analyzedPapers: Paper[]) {
    const paperMap = new Map(analyzedPapers.map(p => [p.doi || p.title, p]));

    papers.value = papers.value.map(p => {
      const key = p.doi || p.title;
      const analyzed = paperMap.get(key);
      if (analyzed) {
        return {
          ...p,
          aiSummary: analyzed.aiSummary,
          aiKeywords: analyzed.aiKeywords,
          methodology: analyzed.methodology,
          conclusion: analyzed.conclusion,
          researchFindings: analyzed.researchFindings
        };
      }
      return p;
    });
  }

  /**
   * 清除文献
   */
  async function clearPapers() {
    papers.value = [];
    try {
      await chrome.runtime.sendMessage({ type: 'CLEAR_PAPERS' });
    } catch (err) {
      console.error('Failed to clear papers:', err);
    }
  }

  /**
   * 刷新额度
   */
  async function refreshQuota() {
    try {
      const response = await chrome.runtime.sendMessage({ type: 'GET_QUOTA' });
      if (response.success) {
        setQuota(response.data);
      }
    } catch (err) {
      console.error('Failed to refresh quota:', err);
    }
  }

  /**
   * 刷新用户信息
   */
  async function refreshUser() {
    try {
      const response = await chrome.runtime.sendMessage({ type: 'GET_PROFILE' });
      if (response.success) {
        setUser(response.data);
      }
    } catch (err) {
      console.error('Failed to refresh user:', err);
    }
  }

  return {
    // State
    user,
    papers,
    currentTask,
    quota,
    isLoading,
    error,
    // Getters
    selectedPapers,
    selectedCount,
    hasPapers,
    hasQuota,
    isLoggedIn,
    // Actions
    setError,
    setLoading,
    setUser,
    setQuota,
    setPapers,
    updatePapers,
    selectAll,
    selectNone,
    selectQ1Only,
    togglePaperSelection,
    loadPapersFromStorage,
    savePapersToStorage,
    updateAnalyzedPapers,
    clearPapers,
    refreshQuota,
    refreshUser
  };
});
