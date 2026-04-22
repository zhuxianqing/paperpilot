import type { User } from './user';

/** API通用响应 */
export interface ApiResponse<T> {
  /** 状态码 */
  code: number;
  /** 消息 */
  message: string;
  /** 数据 */
  data: T;
}

/** 认证响应 */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

/** API错误 */
export class ApiError extends Error {
  code: number;

  constructor(message: string, code: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

/** Chrome消息类型 */
export type ChromeMessageType =
  | 'LOGIN'
  | 'REGISTER'
  | 'SEND_CODE'
  | 'LOGOUT'
  | 'GET_PROFILE'
  | 'GET_QUOTA'
  | 'SAVE_PAPERS'
  | 'LOAD_PAPERS'
  | 'CLEAR_PAPERS'
  | 'ANALYZE_PAPERS'
  | 'EXPORT_FEISHU'
  | 'EXPORT_EXCEL'
  | 'DOWNLOAD_EXCEL'
  | 'GET_AI_CONFIGS'
  | 'SAVE_AI_CONFIG'
  | 'DELETE_AI_CONFIG'
  | 'TEST_AI_CONFIG'
  | 'CREATE_TASK'
  | 'GET_TASK'
  | 'GET_CURRENT_TAB_HOST';

/** Chrome消息 */
export interface ChromeMessage {
  type: ChromeMessageType;
  payload?: unknown;
}

/** Chrome消息响应 */
export interface ChromeMessageResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: string;
}

/** WOS Hook消息 */
export interface WOSHookMessage {
  source: 'paper-pilot-hook';
  type: 'WOS_BATCH_DATA';
  data: string;
  timestamp: number;
}
