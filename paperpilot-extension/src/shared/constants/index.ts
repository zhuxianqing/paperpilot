/**
 * 共享常量
 */

/** 插件名称 */
export const EXTENSION_NAME = 'PaperPilot';

/** 插件版本 */
export const EXTENSION_VERSION = '1.0.0';

/** 免费额度 - 每日次数 */
export const FREE_QUOTA_DAILY = 3;

/** BYOK用户每日上限 */
export const BYOK_DAILY_LIMIT = 50;

/** 默认导出配置 */
export const DEFAULT_EXPORT_CONFIG = {
  maxResults: 50,
  maxResultsLimit: 200
};

/** WOS 相关常量 */
export const WOS_CONSTANTS = {
  /** 核心API匹配模式 - 更灵活的模式 */
  API_PATTERN: 'wosnx',
  /** Hook消息源 */
  HOOK_SOURCE: 'paper-pilot-hook',
  /** Hook消息类型 */
  MESSAGE_TYPE: 'WOS_BATCH_DATA'
};

/** 存储键名 */
export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'accessToken',
  REFRESH_TOKEN: 'refreshToken',
  USER: 'user',
  CACHED_PAPERS: 'cachedPapers',
  AI_CONFIGS: 'aiConfigs',
  SETTINGS: 'settings'
} as const;

/** 事件名称 */
export const EVENTS = {
  PAPERS_UPDATED: 'papers:updated',
  AUTH_STATE_CHANGED: 'auth:stateChanged',
  QUOTA_UPDATED: 'quota:updated'
} as const;
