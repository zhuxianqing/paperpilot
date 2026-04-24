/**
 * Background Service Worker 入口
 */

import type { ChromeMessage, ChromeMessageResponse } from '../shared/types/api';
import type { Paper } from '../shared/types/paper';
import type { CreateTaskRequest } from '../shared/types/task';
import type { UserAIConfig } from '../shared/types/user';
import {
  handleAnalyzePapers,
  handleClearPapers,
  handleCreateTask,
  handleDeleteAIConfig,
  handleDownloadExcel,
  handleExportExcel,
  handleExportFeishu,
  handleGetAIConfigs,
  handleGetCurrentTabHost,
  handleGetProfile,
  handleGetQuota,
  handleGetTask,
  handleGetAnalysisStatus,
  handleGetAnalysisTask,
  handleGetAnalysisTaskPapers,
  handleListAnalysisHistory,
  handleListAnalysisTasks,
  handleLoadPapers,
  handleLookupAnalysisStatus,
  handleLogin,
  handleLogout,
  handleRegister,
  handleSaveAIConfig,
  handleSavePapers,
  handleSendCode,
  handleSubmitAnalysisTask,
  handleTestAIConfig
} from './message-router';

// ===== 生命周期 =====

chrome.runtime.onInstalled.addListener((details) => {
  console.log('[PaperPilot Background] Extension installed', details);

  // 初始化默认设置
  chrome.storage.local.set({
    settings: {
      defaultAIProvider: 'system',
      autoSelectQ1: false
    }
  });
});

chrome.runtime.onStartup.addListener(() => {
  console.log('[PaperPilot Background] Extension started');
});

// ===== 消息处理 =====

chrome.runtime.onMessage.addListener((
  message: ChromeMessage,
  _sender,
  sendResponse
) => {
  const { type, payload } = message;

  console.log('[PaperPilot Background] Received message:', type);

  // 处理异步响应
  const handleAsync = async (handler: () => Promise<ChromeMessageResponse>) => {
    try {
      const response = await handler();
      sendResponse(response);
    } catch (error) {
      sendResponse({
        success: false,
        error: (error as Error).message
      });
    }
  };

  switch (type) {
    // 认证
    case 'LOGIN':
      handleAsync(() => handleLogin(payload as { email: string; password: string }));
      return true;

    case 'REGISTER':
      handleAsync(() => handleRegister(payload as {
        email: string;
        password: string;
        code: string;
        nickname?: string;
      }));
      return true;

    case 'SEND_CODE':
      handleAsync(() => handleSendCode(payload as { email: string }));
      return true;

    case 'LOGOUT':
      handleAsync(() => handleLogout());
      return true;

    // 用户
    case 'GET_PROFILE':
      handleAsync(() => handleGetProfile());
      return true;

    case 'GET_QUOTA':
      handleAsync(() => handleGetQuota());
      return true;

    // 文献
    case 'SAVE_PAPERS':
      handleAsync(() => handleSavePapers(payload as { papers: Paper[] }));
      return true;

    case 'LOAD_PAPERS':
      handleAsync(() => handleLoadPapers());
      return true;

    case 'CLEAR_PAPERS':
      handleAsync(() => handleClearPapers());
      return true;

    // AI 分析
    case 'ANALYZE_PAPERS':
      handleAsync(() => handleAnalyzePapers(payload as {
        papers: Paper[];
        useUserConfig?: boolean;
      }));
      return true;

    case 'SUBMIT_ANALYSIS_TASK':
      handleAsync(() => handleSubmitAnalysisTask(payload as {
        papers: Paper[];
        useUserConfig?: boolean;
      }));
      return true;

    case 'GET_ANALYSIS_TASK':
      handleAsync(() => handleGetAnalysisTask(payload as { taskNo: string }));
      return true;

    case 'GET_ANALYSIS_TASK_PAPERS':
      handleAsync(() => handleGetAnalysisTaskPapers(payload as { taskNo: string }));
      return true;

    case 'LIST_ANALYSIS_TASKS':
      handleAsync(() => handleListAnalysisTasks(payload as { page?: number; size?: number } | undefined));
      return true;

    case 'LIST_ANALYSIS_HISTORY':
      handleAsync(() => handleListAnalysisHistory(payload as { page?: number; size?: number; sortBy?: string; order?: string; keyword?: string } | undefined));
      return true;

    case 'LOOKUP_ANALYSIS_STATUS':
      handleAsync(() => handleLookupAnalysisStatus(payload as { papers: Paper[] }));
      return true;

    // 导出
    case 'EXPORT_FEISHU':
      handleAsync(() => handleExportFeishu(payload as { papers: Paper[] }));
      return true;

    case 'EXPORT_EXCEL':
      handleAsync(() => handleExportExcel(payload as { papers: Paper[] }));
      return true;

    case 'DOWNLOAD_EXCEL':
      handleAsync(() => handleDownloadExcel(payload as { fileId: string }));
      return true;

    // AI 配置 (BYOK)
    case 'GET_AI_CONFIGS':
      handleAsync(() => handleGetAIConfigs());
      return true;

    case 'SAVE_AI_CONFIG':
      handleAsync(() => handleSaveAIConfig(payload as { config: UserAIConfig }));
      return true;

    case 'DELETE_AI_CONFIG':
      handleAsync(() => handleDeleteAIConfig(payload as { provider: string }));
      return true;

    case 'TEST_AI_CONFIG':
      handleAsync(() => handleTestAIConfig(payload as { config: UserAIConfig }));
      return true;

    // 任务
    case 'CREATE_TASK':
      handleAsync(() => handleCreateTask(payload as { request: CreateTaskRequest }));
      return true;

    case 'GET_TASK':
      handleAsync(() => handleGetTask(payload as { taskNo: string }));
      return true;

    case 'GET_CURRENT_TAB_HOST':
      handleAsync(() => handleGetCurrentTabHost());
      return true;

    case 'GET_ANALYSIS_STATUS':
      handleAsync(() => handleGetAnalysisStatus());
      return true;

    default:
      console.warn('[PaperPilot Background] Unknown message type:', type);
      sendResponse({ success: false, error: 'Unknown message type' });
      return false;
  }
});

// ===== Action 点击事件 =====

chrome.action.onClicked.addListener(async (tab) => {
  // 如果 popup 未定义时点击图标，可以执行一些默认行为
  console.log('[PaperPilot Background] Action clicked', tab);
});

// ===== 其他事件监听 =====

// 监听存储变化（用于跨组件通信）
chrome.storage.onChanged.addListener((changes, namespace) => {
  if (namespace === 'local') {
    // 可以在这里处理存储变化事件
    if (changes.cachedPapers) {
      console.log('[PaperPilot Background] Papers updated:',
        changes.cachedPapers.newValue?.length || 0);
    }
  }
});

console.log('[PaperPilot Background] Service Worker loaded');
