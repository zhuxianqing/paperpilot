/** 用户信息 */
export interface User {
  /** 用户ID */
  id: number;
  /** 邮箱 */
  email: string;
  /** 昵称 */
  nickname: string;
  /** 头像URL */
  avatar?: string;
  /** 剩余额度 */
  quotaBalance: number;
  /** 是否VIP */
  isVip: boolean;
  /** VIP过期时间 */
  vipExpireAt?: string;
}

/** 额度信息 */
export interface QuotaInfo {
  /** 剩余付费额度 */
  balance: number;
  /** 今日已用免费额度 */
  freeQuotaUsed: number;
  /** 免费额度总数 */
  freeQuotaTotal: number;
}

/** 用户AI配置 (BYOK) — 后端返回的列表数据（不含apiKey） */
export interface UserAIConfigSummary {
  /** 配置ID */
  id?: number;
  /** AI提供商 */
  provider: 'openai' | 'deepseek' | 'claude' | 'glm' | 'custom';
  /** 自定义Base URL */
  baseUrl?: string;
  /** 模型名称 */
  model: string;
  /** 是否活跃 */
  isActive?: boolean;
  /** 最后测试时间 */
  lastTestedAt?: string;
  /** 测试状态 */
  testStatus?: string;
  /** 是否设为默认 */
  isDefault?: boolean;
}

/** 用户AI配置 (BYOK) — 编辑/保存时使用（含apiKey） */
export interface UserAIConfig extends UserAIConfigSummary {
  /** API Key */
  apiKey: string;
}

/** 登录请求 */
export interface LoginRequest {
  email: string;
  password: string;
}

/** 注册请求 */
export interface RegisterRequest {
  email: string;
  code: string;
  password: string;
  nickname?: string;
}
