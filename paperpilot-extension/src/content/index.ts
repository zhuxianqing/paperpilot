/**
 * Content Script 入口
 * 运行在 ISOLATED world，负责与页面脚本通信并处理数据
 */

import { WOS_CONSTANTS } from '../shared/constants';
import type { WOSHookMessage } from '../shared/types/api';
import { parseWOSData } from './parser';

// ===== 初始化 =====

console.log('[PaperPilot Content] Script loaded');

// 注入 Hook 脚本
injectHookScript();

// 监听来自 hook 脚本的消息
setupMessageListener();

// ===== 函数定义 =====

/**
 * 注入 Hook 脚本到页面
 * 由于 Manifest V3 的限制，我们通过动态创建 script 标签注入 MAIN world 脚本
 */
function injectHookScript(): void {
  try {
    // 检查是否已注入
    if (document.querySelector('script[data-paper-pilot-hook]')) {
      console.log('[PaperPilot Content] Hook already injected');
      return;
    }

    // 创建 script 标签
    const script = document.createElement('script');
    script.src = chrome.runtime.getURL('content/hook-inject.js');
    script.dataset.paperPilotHook = 'true';
    script.onload = () => {
      // 脚本加载后移除
      script.remove();
      console.log('[PaperPilot Content] Hook script injected');
    };
    script.onerror = (error) => {
      console.error('[PaperPilot Content] Failed to inject hook script:', error);
    };

    // 插入到页面
    const target = document.head || document.documentElement;
    if (target) {
      target.appendChild(script);
    }
  } catch (error) {
    console.error('[PaperPilot Content] Error injecting hook:', error);
  }
}

/**
 * 设置消息监听器
 * 监听来自 hook 脚本的消息
 */
function setupMessageListener(): void {
  window.addEventListener('message', async (event: MessageEvent) => {
    // 安全检查：只处理来自当前窗口的消息
    if (event.source !== window) return;

    const message = event.data as WOSHookMessage;

    // 验证消息来源
    if (message?.source !== WOS_CONSTANTS.HOOK_SOURCE) return;
    if (message?.type !== WOS_CONSTANTS.MESSAGE_TYPE) return;

    console.log('[PaperPilot Content] Received WOS data, length:', message.data?.length);

    try {
      // 解析数据
      const papers = parseWOSData(message.data);

      if (papers.length === 0) {
        console.warn('[PaperPilot Content] No papers parsed from API data');
        return;
      }

      console.log(`[PaperPilot Content] Parsed ${papers.length} papers`);

      // 存储到 extension storage
      await savePapersToStorage(papers);

      // 通知 popup/background
      notifyDataReady(papers.length);
    } catch (error) {
      console.error('[PaperPilot Content] Error processing WOS data:', error);
    }
  });
}

/**
 * 保存文献到 extension storage
 * @param papers 文献列表
 */
async function savePapersToStorage(papers: unknown[]): Promise<void> {
  try {
    await chrome.storage.local.set({
      cachedPapers: papers,
      cachedPapersHost: window.location.hostname,
      papersUpdatedAt: Date.now()
    });
    console.log('[PaperPilot Content] Papers saved to storage');
  } catch (error) {
    console.error('[PaperPilot Content] Failed to save papers:', error);
  }
}

/**
 * 通知数据已就绪
 * @param count 文献数量
 */
function notifyDataReady(count: number): void {
  try {
    chrome.runtime.sendMessage({
      type: 'PAPERS_UPDATED',
      payload: { count, timestamp: Date.now() }
    }).catch((error) => {
      // 忽略 "Could not establish connection" 错误（popup 可能未打开）
      if (error.message?.includes('Could not establish connection')) {
        return;
      }
      console.error('[PaperPilot Content] Failed to notify:', error);
    });
  } catch (error) {
    console.error('[PaperPilot Content] Error sending message:', error);
  }
}

/**
 * 监听来自 popup/background 的消息
 */
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  const { type } = message;

  switch (type) {
    case 'GET_PAGE_PAPERS':
      handleGetPagePapers(sendResponse);
      return true; // 异步响应

    case 'CHECK_WOS_PAGE':
      handleCheckWOSPage(sendResponse);
      return true;

    default:
      return false;
  }
});

/**
 * 处理获取页面文献请求
 */
async function handleGetPagePapers(sendResponse: (response: unknown) => void): Promise<void> {
  try {
    const result = await chrome.storage.local.get('cachedPapers');
    sendResponse({
      success: true,
      data: result.cachedPapers || []
    });
  } catch (error) {
    sendResponse({
      success: false,
      error: (error as Error).message
    });
  }
}

/**
 * 检查当前是否是 WOS 页面
 */
function handleCheckWOSPage(sendResponse: (response: unknown) => void): void {
  const isWOS = window.location.href.includes('webofscience.com');
  const hasSearchResults = document.querySelector('[data-testid="search-results"]') !== null ||
                          document.querySelector('.search-results-content') !== null ||
                          document.querySelector('[id*="search"]') !== null;

  sendResponse({
    success: true,
    data: {
      isWOS,
      hasSearchResults,
      url: window.location.href
    }
  });
}

export {};
