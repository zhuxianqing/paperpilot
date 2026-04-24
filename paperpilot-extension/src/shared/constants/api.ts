/**
 * API 常量配置
 */

/** API 基础地址 */
// Chrome extension 构建后无法使用 import.meta.env.DEV，默认使用开发环境 localhost
// 生产构建时注释掉下面这行，启用生产环境
export const API_BASE_URL = 'http://localhost:8080/api/v1';
// export const API_BASE_URL = 'https://api.paperpilot.com/api/v1';

/** API 端点 */
export const API_ENDPOINTS = {
  // 认证
  AUTH: {
    LOGIN: '/auth/login',
    REGISTER: '/auth/register',
    SEND_CODE: '/auth/send-code',
    REFRESH: '/auth/refresh',
    LOGOUT: '/auth/logout'
  },
  // 用户
  USER: {
    PROFILE: '/user/profile',
    QUOTA: '/user/quota',
    TRANSACTIONS: '/user/transactions',
    API_KEYS: '/user/api-keys'
  },
  // AI配置 (BYOK)
  AI_CONFIG: {
    LIST: '/user/ai-configs',
    SAVE: '/user/ai-configs',
    DELETE: (provider: string) => `/user/ai-configs/${provider}`,
    TEST: '/user/ai-configs/test'
  },
  // 任务
  TASK: {
    CREATE: '/tasks/search',
    GET: (taskNo: string) => `/tasks/${taskNo}`,
    LIST: '/tasks/list',
    PAPERS: (taskNo: string) => `/tasks/${taskNo}/papers`,
    DOWNLOAD: (taskNo: string) => `/tasks/${taskNo}/download`
  },
  // AI分析
  AI: {
    ANALYZE_BATCH: '/ai/analyze-batch',
    ANALYSIS_TASKS: '/ai/analysis-tasks',
    ANALYSIS_TASK: (taskNo: string) => `/ai/analysis-tasks/${taskNo}`,
    ANALYSIS_TASK_PAPERS: (taskNo: string) => `/ai/analysis-tasks/${taskNo}/papers`,
    ANALYSIS_TASK_HISTORY: '/ai/analysis-tasks/history',
    ANALYSIS_HISTORY: '/ai/analyses/history',
    ANALYSIS_LOOKUP: '/ai/analyses/lookup'
  },
  // 导出
  EXPORT: {
    FEISHU: '/export/feishu',
    EXCEL: '/export/excel',
    DOWNLOAD: (fileId: string) => `/export/download/${fileId}`
  },
  // 支付
  PAYMENT: {
    PACKAGES: '/payment/packages',
    CREATE_ORDER: '/payment/orders',
    GET_ORDER: (orderNo: string) => `/payment/orders/${orderNo}`,
    ALIPAY_CREATE: '/payment/alipay/create'
  }
} as const;

/** AI 提供商 */
export const AI_PROVIDERS = [
  { value: 'openai', label: 'OpenAI', models: ['gpt-4', 'gpt-4-turbo', 'gpt-3.5-turbo'] },
  { value: 'deepseek', label: 'DeepSeek', models: ['deepseek-chat', 'deepseek-coder'] },
  { value: 'claude', label: 'Claude', models: ['claude-3-opus', 'claude-3-sonnet', 'claude-3-haiku'] },
  { value: 'glm', label: '智谱GLM', models: ['glm-4', 'glm-3-turbo'] },
  { value: 'custom', label: '自定义', models: [] }
] as const;

/** 默认模型 */
export const DEFAULT_MODELS: Record<string, string> = {
  openai: 'gpt-4',
  deepseek: 'deepseek-chat',
  claude: 'claude-3-sonnet',
  glm: 'glm-4',
  custom: ''
};

/** 请求超时时间 (毫秒) */
export const REQUEST_TIMEOUT = 60000;

/** 重试次数 */
export const MAX_RETRY_ATTEMPTS = 3;
