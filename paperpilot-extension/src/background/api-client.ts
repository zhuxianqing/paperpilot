/**
 * API 客户端
 * 封装与后端 API 的通信
 */

import { API_BASE_URL, API_ENDPOINTS } from '../shared/constants/api';
import type { ApiResponse, AuthResponse } from '../shared/types/api';
import type { Paper } from '../shared/types/paper';
import type { CreateTaskRequest, SearchTask, AnalysisTaskSummary, AnalysisPaperResult, PaginatedResult } from '../shared/types/task';
import type { QuotaInfo, User, UserAIConfig } from '../shared/types/user';

export interface AnalysisLookupResult {
  paperKey: string;
  paperDoi?: string;
  analyzed: boolean;
  analyzedAt?: string;
  status?: 'pending' | 'processing' | 'completed' | 'failed';
}

class APIClient {
  private baseURL = API_BASE_URL;

  /**
   * 发送 HTTP 请求
   */
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const token = await this.getToken();

    const url = `${this.baseURL}${endpoint}`;
    const config: RequestInit = {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` }),
        ...options.headers
      }
    };

    const response = await fetch(url, config);

    // 处理 401 未授权
    if (response.status === 401) {
      // 清除 token，让用户重新登录
      await chrome.storage.local.remove(['accessToken', 'refreshToken']);
      throw new Error('登录已过期，请重新登录');
    }

    const data: ApiResponse<T> = await response.json();

    if (!response.ok || data.code !== 200) {
      throw new Error(data.message || 'Request failed');
    }

    return data.data;
  }

  /**
   * 获取存储的 token
   */
  private async getToken(): Promise<string | null> {
    const result = await chrome.storage.local.get('accessToken');
    return result.accessToken || null;
  }

  // ===== 认证相关 =====

  /**
   * 登录
   */
  async login(email: string, password: string): Promise<AuthResponse> {
    return this.request(API_ENDPOINTS.AUTH.LOGIN, {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
  }

  /**
   * 注册
   */
  async register(
    email: string,
    password: string,
    code: string,
    nickname?: string
  ): Promise<AuthResponse> {
    return this.request(API_ENDPOINTS.AUTH.REGISTER, {
      method: 'POST',
      body: JSON.stringify({ email, password, code, nickname })
    });
  }

  /**
   * 发送验证码
   */
  async sendCode(email: string): Promise<void> {
    await this.request(API_ENDPOINTS.AUTH.SEND_CODE, {
      method: 'POST',
      body: JSON.stringify({ email })
    });
  }

  /**
   * 刷新 token
   */
  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    return this.request(API_ENDPOINTS.AUTH.REFRESH, {
      method: 'POST',
      headers: { 'X-Refresh-Token': refreshToken }
    });
  }

  // ===== 用户相关 =====

  /**
   * 获取用户信息
   */
  async getProfile(): Promise<User> {
    return this.request(API_ENDPOINTS.USER.PROFILE);
  }

  /**
   * 获取额度信息
   */
  async getQuota(): Promise<QuotaInfo> {
    return this.request(API_ENDPOINTS.USER.QUOTA);
  }

  // ===== AI 配置 (BYOK) =====

  /**
   * 获取用户 AI 配置列表
   */
  async getAIConfigs(): Promise<UserAIConfig[]> {
    return this.request(API_ENDPOINTS.AI_CONFIG.LIST);
  }

  /**
   * 保存 AI 配置
   */
  async saveAIConfig(config: UserAIConfig): Promise<void> {
    await this.request(API_ENDPOINTS.AI_CONFIG.SAVE, {
      method: 'POST',
      body: JSON.stringify(config)
    });
  }

  /**
   * 删除 AI 配置
   */
  async deleteAIConfig(provider: string): Promise<void> {
    await this.request(API_ENDPOINTS.AI_CONFIG.DELETE(provider), {
      method: 'DELETE'
    });
  }

  /**
   * 测试 AI 配置
   */
  async testAIConfig(
    config: UserAIConfig
  ): Promise<{ success: boolean; message: string }> {
    return this.request(API_ENDPOINTS.AI_CONFIG.TEST, {
      method: 'POST',
      body: JSON.stringify(config)
    });
  }

  // ===== 任务相关 =====

  /**
   * 创建搜索任务
   */
  async createTask(request: CreateTaskRequest): Promise<SearchTask> {
    return this.request(API_ENDPOINTS.TASK.CREATE, {
      method: 'POST',
      body: JSON.stringify(request)
    });
  }

  /**
   * 获取任务详情
   */
  async getTask(taskNo: string): Promise<SearchTask> {
    return this.request(API_ENDPOINTS.TASK.GET(taskNo));
  }

  // ===== AI 分析 =====

  /**
   * 提交异步分析任务
   */
  async submitAnalysisTask(
    papers: Paper[],
    useUserConfig?: boolean,
    config?: UserAIConfig
  ): Promise<AnalysisTaskSummary> {
    return this.request(API_ENDPOINTS.AI.ANALYSIS_TASKS, {
      method: 'POST',
      body: JSON.stringify({
        papers: papers.map((paper) => ({
          doi: paper.doi,
          title: paper.title,
          authors: paper.authors,
          abstracts: paper.abstract,
          journal: paper.journal,
          publishYear: paper.publishYear,
          quartile: paper.quartile,
          citations: paper.citations,
          sourceUrl: paper.sourceUrl,
          pdfUrl: paper.pdfUrl,
          paperKey: paper.paperKey,
          reuseDecision: paper.reuseDecision,
          forceReanalyze: paper.forceReanalyze
        })),
        useUserConfig,
        config
      })
    });
  }

  /**
   * 获取分析任务详情
   */
  async getAnalysisTask(taskNo: string): Promise<AnalysisTaskSummary> {
    return this.request(API_ENDPOINTS.AI.ANALYSIS_TASK(taskNo));
  }

  /**
   * 获取分析任务下的论文结果
   */
  async getAnalysisTaskPapers(taskNo: string): Promise<AnalysisPaperResult[]> {
    return this.request(API_ENDPOINTS.AI.ANALYSIS_TASK_PAPERS(taskNo));
  }

  /**
   * 获取分析任务历史
   */
  async listAnalysisTasks(page = 1, size = 20): Promise<PaginatedResult<AnalysisTaskSummary>> {
    return this.request(`${API_ENDPOINTS.AI.ANALYSIS_TASK_HISTORY}?page=${page}&size=${size}`);
  }

  /**
   * 获取单篇分析历史
   */
  async listAnalysisHistory(page = 1, size = 20, sortBy?: string, order?: string, keyword?: string): Promise<PaginatedResult<AnalysisPaperResult>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (sortBy) params.set('sortBy', sortBy);
    if (order) params.set('order', order);
    if (keyword) params.set('keyword', keyword);
    return this.request(`${API_ENDPOINTS.AI.ANALYSIS_HISTORY}?${params.toString()}`);
  }

  async lookupAnalysisStatus(papers: Paper[]): Promise<AnalysisLookupResult[]> {
    return this.request(API_ENDPOINTS.AI.ANALYSIS_LOOKUP, {
      method: 'POST',
      body: JSON.stringify(papers.map((paper) => ({
        doi: paper.doi,
        title: paper.title,
        authors: paper.authors,
        abstracts: paper.abstract,
        journal: paper.journal,
        publishYear: paper.publishYear,
        sourceUrl: paper.sourceUrl,
        pdfUrl: paper.pdfUrl,
        paperKey: paper.paperKey
      })))
    });
  }

  // ===== 导出 =====

  /**
   * 导出到飞书文档
   */
  async exportToFeishu(papers: Paper[]): Promise<{ docUrl: string }> {
    return this.request(API_ENDPOINTS.EXPORT.FEISHU, {
      method: 'POST',
      body: JSON.stringify({ papers })
    });
  }

  /**
   * 导出到 Excel（本地存储模式）
   * 返回 fileId 用于后续下载
   */
  async exportToExcel(
    papers: Paper[]
  ): Promise<{ fileId: string; expiresAt: string }> {
    return this.request(API_ENDPOINTS.EXPORT.EXCEL, {
      method: 'POST',
      body: JSON.stringify({ papers })
    });
  }

  /**
   * 下载 Excel 文件（流式下载）
   */
  async downloadExcel(fileId: string): Promise<Blob> {
    const token = await this.getToken();
    const url = `${this.baseURL}${API_ENDPOINTS.EXPORT.DOWNLOAD(fileId)}`;

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    if (!response.ok) {
      if (response.status === 401) {
        await chrome.storage.local.remove(['accessToken', 'refreshToken']);
        throw new Error('登录已过期，请重新登录');
      }
      const errorData = await response.json().catch(() => ({ message: '下载失败' }));
      throw new Error(errorData.message || '下载失败');
    }

    return response.blob();
  }

  // ===== 支付 =====

  /**
   * 获取额度套餐
   */
  async getQuotaPackages(): Promise<
    Array<{
      id: number;
      name: string;
      price: number;
      quotaAmount: number;
      description: string;
    }>
  > {
    return this.request(API_ENDPOINTS.PAYMENT.PACKAGES);
  }

  /**
   * 创建订单
   */
  async createOrder(packageId: number): Promise<{ orderNo: string }> {
    return this.request(API_ENDPOINTS.PAYMENT.CREATE_ORDER, {
      method: 'POST',
      body: JSON.stringify({ packageId })
    });
  }

  /**
   * 创建支付宝支付
   */
  async createAlipayPayment(
    orderNo: string
  ): Promise<{ formHtml: string }> {
    return this.request(API_ENDPOINTS.PAYMENT.ALIPAY_CREATE, {
      method: 'POST',
      body: JSON.stringify({ orderNo })
    });
  }
}

export const api = new APIClient();
