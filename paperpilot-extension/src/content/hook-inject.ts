/**
 * Fetch Hook 注入脚本
 * 在 MAIN world 中运行，用于拦截 WOS API 响应
 */

// 调试模式 - 生产环境应关闭
const DEBUG_MODE = true;

// 常量直接内联，避免 ES module import 问题
const API_PATTERN = 'wosnx';
const HOOK_SOURCE = 'paper-pilot-hook';
const MESSAGE_TYPE = 'WOS_BATCH_DATA';

// 全局变量声明
declare global {
  interface Window {
    __paperPilotHookInjected?: boolean;
    __paperPilotDebugUrls?: string[];
  }
}

(function injectFetchHook() {
  try {
    // 保存原始 fetch
    const originalFetch = window.fetch;

    // 初始化调试URL列表
    window.__paperPilotDebugUrls = [];

    // 重写 fetch
    window.fetch = async function fetchHook(
      input: RequestInfo | URL,
      init?: RequestInit
    ): Promise<Response> {
      // 调用原始 fetch
      const response = await originalFetch.apply(this, [input, init]);

      try {
        // 获取请求的 URL
        const url = typeof input === 'string'
          ? input
          : input instanceof URL
            ? input.href
            : input.url;

        // 调试模式：记录所有 fetch 请求
        if (DEBUG_MODE && typeof url === 'string') {
          window.__paperPilotDebugUrls!.push(url);
          console.log('[PaperPilot Hook] Intercepted fetch:', url);
        }

        // 检查是否是 WOS 核心 API
        if (typeof url === 'string' && url.includes(API_PATTERN)) {
          console.log('[PaperPilot Hook] WOS API detected:', url);

          // 克隆响应（因为 response 只能读取一次）
          const clonedResponse = response.clone();

          // 读取响应文本并发送给 content script
          clonedResponse.text().then((text: string) => {
            console.log('[PaperPilot Hook] WOS API response length:', text.length);

            window.postMessage({
              source: HOOK_SOURCE,
              type: MESSAGE_TYPE,
              data: text,
              timestamp: Date.now(),
              url: url
            }, '*');

            console.log('[PaperPilot Hook] postMessage sent');
          }).catch((err: Error) => {
            console.error('[PaperPilot Hook] Failed to read response:', err);
          });
        }
      } catch (error) {
        console.error('[PaperPilot Hook] Error in fetch hook:', error);
      }

      return response;
    };

    // 标记已注入
    window.__paperPilotHookInjected = true;

    console.log('[PaperPilot] Fetch hook injected successfully');
  } catch (error) {
    console.error('[PaperPilot] Fetch hook injection failed:', error);
  }
})();

// 使此文件成为模块，以便全局声明生效
export {};
