/**
 * 全局状态管理
 */

import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Paper } from '@shared/types/paper';
import type { QuotaInfo, User } from '@shared/types/user';
import type { SearchTask } from '@shared/types/task';
import {
  getSelectedPaperDOIs,
  setSelectedPaperDOIs,
  clearSelectedPaperDOIs
} from '@shared/utils/storage';

export const useAppStore = defineStore('app', () => {
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

  const selectedPapers = computed(() => papers.value.filter(p => p.selected));
  const selectedCount = computed(() => selectedPapers.value.length);
  const hasPapers = computed(() => papers.value.length > 0);
  const hasQuota = computed(() =>
    quota.value.balance > 0 ||
    quota.value.freeQuotaTotal > quota.value.freeQuotaUsed
  );
  const isLoggedIn = computed(() => !!user.value);

  function buildPaperKey(paper: Paper): string {
    if (paper.paperKey) return paper.paperKey;
    if (paper.doi?.trim()) return paper.doi.trim().toLowerCase();
    const firstAuthor = paper.authors?.[0] || '';
    return `${paper.title.trim().toLowerCase()}|${firstAuthor}|${paper.publishYear || ''}`;
  }

  function setError(message: string | null) {
    error.value = message;
  }

  function setLoading(loading: boolean) {
    isLoading.value = loading;
  }

  function setUser(userData: User | null) {
    user.value = userData;
  }

  function setQuota(quotaData: QuotaInfo) {
    quota.value = quotaData;
  }

  function setPapers(papersData: Paper[]) {
    papers.value = papersData.map((p, idx) => ({
      ...p,
      id: p.id || idx + 1,
      paperKey: buildPaperKey(p),
      isAnalyzed: p.isAnalyzed ?? false,
      reuseDecision: p.reuseDecision ?? (p.isAnalyzed ? 'reuse' : 'reanalyze'),
      forceReanalyze: p.forceReanalyze ?? false,
      selected: p.selected ?? true
    }));
  }

  function updatePapers(updatedPapers: Paper[]) {
    papers.value = updatedPapers;
  }

  async function applyBatchReuseDecision(decision: 'reuse' | 'reanalyze', onlySelected = true) {
    papers.value = papers.value.map((paper) => {
      if (!paper.isAnalyzed) return paper;
      if (onlySelected && !paper.selected) return paper;
      return {
        ...paper,
        reuseDecision: decision,
        forceReanalyze: decision === 'reanalyze'
      };
    });
    await persistSelection();
  }

  function setPaperReuseDecision(paperKey: string, decision: 'reuse' | 'reanalyze') {
    papers.value = papers.value.map((paper) =>
      paper.paperKey === paperKey
        ? {
            ...paper,
            reuseDecision: decision,
            forceReanalyze: decision === 'reanalyze'
          }
        : paper
    );
  }

  async function selectAll() {
    papers.value = papers.value.map(p => ({ ...p, selected: true }));
    await persistSelection();
  }

  async function selectNone() {
    papers.value = papers.value.map(p => ({ ...p, selected: false }));
    await persistSelection();
  }

  async function selectQ1Only() {
    papers.value = papers.value.map(p => ({
      ...p,
      selected: p.quartile === 'Q1'
    }));
    await persistSelection();
  }

  async function persistSelection() {
    const dois = papers.value.filter(p => p.selected).map(p => p.doi).filter(Boolean) as string[];
    await setSelectedPaperDOIs(dois);
  }

  async function togglePaperSelection(paperKeyOrDoi: string | undefined) {
    const paper = papers.value.find(p => p.paperKey === paperKeyOrDoi || p.doi === paperKeyOrDoi);
    if (paper) {
      paper.selected = !paper.selected;
      await persistSelection();
    }
  }

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

  async function lookupAnalysisStatus() {
    if (papers.value.length === 0) return;
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'LOOKUP_ANALYSIS_STATUS',
        payload: {
          papers: papers.value
        }
      });
      if (!response.success || !response.data) return;
      const lookupMap = new Map(
        (response.data as Array<{ paperKey: string; analyzed: boolean; analyzedAt?: string; status?: Paper['analysisStatus'] }>).map(item => [item.paperKey, item])
      );
      papers.value = papers.value.map((paper) => {
        const key = buildPaperKey(paper);
        const lookup = lookupMap.get(key);
        const isAnalyzed = !!lookup?.analyzed;
        return {
          ...paper,
          paperKey: key,
          isAnalyzed,
          analysisStatus: lookup?.status,
          analyzedAt: lookup?.analyzedAt,
          reuseDecision: isAnalyzed ? (paper.reuseDecision || 'reuse') : 'reanalyze',
          forceReanalyze: isAnalyzed ? paper.forceReanalyze ?? false : false
        };
      });
    } catch (err) {
      console.error('Failed to lookup analysis status:', err);
    }
  }

  async function clearPapers() {
    papers.value = [];
    await clearSelectedPaperDOIs();
    try {
      await chrome.runtime.sendMessage({ type: 'CLEAR_PAPERS' });
    } catch (err) {
      console.error('Failed to clear papers:', err);
    }
  }

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

  async function restoreSelectionFromStorage() {
    const dois = await getSelectedPaperDOIs();
    if (dois.length > 0) {
      papers.value = papers.value.map(p => ({
        ...p,
        selected: dois.includes(p.doi || '')
      }));
    }
  }

  return {
    user,
    papers,
    currentTask,
    quota,
    isLoading,
    error,
    selectedPapers,
    selectedCount,
    hasPapers,
    hasQuota,
    isLoggedIn,
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
    setPaperReuseDecision,
    applyBatchReuseDecision,
    loadPapersFromStorage,
    lookupAnalysisStatus,
    clearPapers,
    refreshQuota,
    refreshUser,
    restoreSelectionFromStorage
  };
});
