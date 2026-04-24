/**
 * 消息路由器
 * 处理来自 popup 和 content script 的消息
 */

import type { ChromeMessageResponse } from '../shared/types/api';
import type { Paper } from '../shared/types/paper';
import type { CreateTaskRequest, UserAIConfig } from '../shared/types';
import type { AnalysisTask } from '../shared/types/task';
import { api } from './api-client';

/**
 * 处理登录
 */
export async function handleGetCurrentTabHost(): Promise<ChromeMessageResponse> {
  try {
    const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
    const url = tabs[0]?.url || '';
    return { success: true, data: url };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理登录
 */
export async function handleLogin(payload: {
  email: string;
  password: string;
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.login(payload.email, payload.password);

    // 保存认证信息
    await chrome.storage.local.set({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: data.user
    });

    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理注册
 */
export async function handleRegister(payload: {
  email: string;
  password: string;
  code: string;
  nickname?: string;
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.register(
      payload.email,
      payload.password,
      payload.code,
      payload.nickname
    );

    // 保存认证信息
    await chrome.storage.local.set({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: data.user
    });

    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理获取用户信息
 */
export async function handleGetProfile(): Promise<ChromeMessageResponse> {
  try {
    const data = await api.getProfile();
    await chrome.storage.local.set({ user: data });
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理获取额度信息
 */
export async function handleGetQuota(): Promise<ChromeMessageResponse> {
  try {
    const data = await api.getQuota();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理保存文献
 */
export async function handleSavePapers(payload: {
  papers: Paper[];
}): Promise<ChromeMessageResponse> {
  try {
    await chrome.storage.local.set({
      cachedPapers: payload.papers,
      papersUpdatedAt: Date.now()
    });
    return { success: true };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理加载文献
 */
export async function handleLoadPapers(): Promise<ChromeMessageResponse> {
  try {
    const result = await chrome.storage.local.get('cachedPapers');
    return { success: true, data: result.cachedPapers || [] };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理清除文献
 */
export async function handleClearPapers(): Promise<ChromeMessageResponse> {
  try {
    await chrome.storage.local.remove('cachedPapers');
    return { success: true };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 兼容旧消息：转发为异步任务提交
 */
export async function handleAnalyzePapers(payload: {
  papers: Paper[];
  useUserConfig?: boolean;
  config?: UserAIConfig;
}): Promise<ChromeMessageResponse> {
  return handleSubmitAnalysisTask(payload);
}

/**
 * 获取分析任务状态
 */
export async function handleGetAnalysisStatus(): Promise<ChromeMessageResponse> {
  try {
    const result = await chrome.storage.local.get('analysisTask');
    const task = result.analysisTask as AnalysisTask | undefined;
    return { success: true, data: task || null };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 提交异步分析任务
 */
export async function handleSubmitAnalysisTask(payload: {
  papers: Paper[];
  useUserConfig?: boolean;
  config?: UserAIConfig;
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.submitAnalysisTask(payload.papers, payload.useUserConfig, payload.config);
    await chrome.storage.local.set({ currentAnalysisTaskNo: data.taskNo });
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

export async function handleLookupAnalysisStatus(payload: {
  papers: Paper[];
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.lookupAnalysisStatus(payload.papers);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 获取异步分析任务详情
 */
export async function handleGetAnalysisTask(payload: { taskNo: string }): Promise<ChromeMessageResponse> {
  try {
    const data = await api.getAnalysisTask(payload.taskNo);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 获取异步分析任务下的论文结果
 */
export async function handleGetAnalysisTaskPapers(payload: { taskNo: string }): Promise<ChromeMessageResponse> {
  try {
    const data = await api.getAnalysisTaskPapers(payload.taskNo);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 获取异步分析任务历史
 */
export async function handleListAnalysisTasks(payload?: { page?: number; size?: number }): Promise<ChromeMessageResponse> {
  try {
    const data = await api.listAnalysisTasks(payload?.page ?? 1, payload?.size ?? 20);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 获取单篇分析历史
 */
export async function handleListAnalysisHistory(payload?: { page?: number; size?: number; sortBy?: string; order?: string; keyword?: string }): Promise<ChromeMessageResponse> {
  try {
    const data = await api.listAnalysisHistory(payload?.page ?? 1, payload?.size ?? 20, payload?.sortBy, payload?.order, payload?.keyword);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理导出到飞书
 */
export async function handleExportFeishu(payload: {
  papers: Paper[];
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.exportToFeishu(payload.papers);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理导出到 Excel
 */
export async function handleExportExcel(payload: {
  papers: Paper[];
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.exportToExcel(payload.papers);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理下载 Excel 文件
 */
export async function handleDownloadExcel(payload: {
  fileId: string;
}): Promise<ChromeMessageResponse> {
  try {
    const blob = await api.downloadExcel(payload.fileId);
    return { success: true, data: blob };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理获取 AI 配置
 */
export async function handleGetAIConfigs(): Promise<ChromeMessageResponse> {
  try {
    const data = await api.getAIConfigs();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理保存 AI 配置
 */
export async function handleSaveAIConfig(payload: {
  config: UserAIConfig;
}): Promise<ChromeMessageResponse> {
  try {
    await api.saveAIConfig(payload.config);
    return { success: true };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理删除 AI 配置
 */
export async function handleDeleteAIConfig(payload: {
  provider: string;
}): Promise<ChromeMessageResponse> {
  try {
    await api.deleteAIConfig(payload.provider);
    return { success: true };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理测试 AI 配置
 */
export async function handleTestAIConfig(payload: {
  config: UserAIConfig;
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.testAIConfig(payload.config);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理创建任务
 */
export async function handleCreateTask(payload: {
  request: CreateTaskRequest;
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.createTask(payload.request);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理获取任务
 */
export async function handleGetTask(payload: {
  taskNo: string;
}): Promise<ChromeMessageResponse> {
  try {
    const data = await api.getTask(payload.taskNo);
    return { success: true, data };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理发送验证码
 */
export async function handleSendCode(payload: {
  email: string;
}): Promise<ChromeMessageResponse> {
  try {
    await api.sendCode(payload.email);
    return { success: true };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

/**
 * 处理退出登录
 */
export async function handleLogout(): Promise<ChromeMessageResponse> {
  try {
    await chrome.storage.local.remove([
      'accessToken',
      'refreshToken',
      'user',
      'cachedPapers',
      'cachedPapersHost',
      'selectedPaperDOIs',
      'analyzedPapers',
      'currentView',
      'analysisTask',
      'currentAnalysisTaskNo'
    ]);
    return { success: true };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}
