import type { Paper } from '../types/paper';
import type { User, UserAIConfig } from '../types/user';

/**
 * Chrome Storage 封装
 * 提供类型安全的存储操作
 */

// 存储键名常量
export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'accessToken',
  REFRESH_TOKEN: 'refreshToken',
  USER: 'user',
  CACHED_PAPERS: 'cachedPapers',
  AI_CONFIGS: 'aiConfigs',
  SETTINGS: 'settings'
} as const;

// 存储数据类型定义
interface StorageData {
  [STORAGE_KEYS.ACCESS_TOKEN]: string;
  [STORAGE_KEYS.REFRESH_TOKEN]: string;
  [STORAGE_KEYS.USER]: User;
  [STORAGE_KEYS.CACHED_PAPERS]: Paper[];
  [STORAGE_KEYS.AI_CONFIGS]: UserAIConfig[];
  [STORAGE_KEYS.SETTINGS]: Record<string, unknown>;
}

/**
 * 获取存储项
 */
export async function getStorageItem<K extends keyof StorageData>(
  key: K
): Promise<StorageData[K] | null> {
  try {
    const result = await chrome.storage.local.get(key);
    return result[key] ?? null;
  } catch (error) {
    console.error(`[PaperPilot] Failed to get storage item: ${key}`, error);
    return null;
  }
}

/**
 * 设置存储项
 */
export async function setStorageItem<K extends keyof StorageData>(
  key: K,
  value: StorageData[K]
): Promise<void> {
  try {
    await chrome.storage.local.set({ [key]: value });
  } catch (error) {
    console.error(`[PaperPilot] Failed to set storage item: ${key}`, error);
    throw error;
  }
}

/**
 * 移除存储项
 */
export async function removeStorageItem(key: keyof StorageData): Promise<void> {
  try {
    await chrome.storage.local.remove(key);
  } catch (error) {
    console.error(`[PaperPilot] Failed to remove storage item: ${key}`, error);
    throw error;
  }
}

/**
 * 清除所有存储
 */
export async function clearStorage(): Promise<void> {
  try {
    await chrome.storage.local.clear();
  } catch (error) {
    console.error('[PaperPilot] Failed to clear storage', error);
    throw error;
  }
}

/**
 * 获取多个存储项
 */
export async function getMultipleStorageItems<K extends keyof StorageData>(
  keys: K[]
): Promise<Pick<StorageData, K>> {
  try {
    return await chrome.storage.local.get(keys) as Pick<StorageData, K>;
  } catch (error) {
    console.error('[PaperPilot] Failed to get multiple storage items', error);
    throw error;
  }
}

/**
 * 设置多个存储项
 */
export async function setMultipleStorageItems<K extends keyof StorageData>(
  items: Pick<StorageData, K>
): Promise<void> {
  try {
    await chrome.storage.local.set(items);
  } catch (error) {
    console.error('[PaperPilot] Failed to set multiple storage items', error);
    throw error;
  }
}

// ===== 便捷方法 =====

/**
 * 获取访问令牌
 */
export async function getAccessToken(): Promise<string | null> {
  return getStorageItem(STORAGE_KEYS.ACCESS_TOKEN);
}

/**
 * 设置访问令牌
 */
export async function setAccessToken(token: string): Promise<void> {
  return setStorageItem(STORAGE_KEYS.ACCESS_TOKEN, token);
}

/**
 * 获取用户信息
 */
export async function getUser(): Promise<User | null> {
  return getStorageItem(STORAGE_KEYS.USER);
}

/**
 * 设置用户信息
 */
export async function setUser(user: User): Promise<void> {
  return setStorageItem(STORAGE_KEYS.USER, user);
}

/**
 * 获取缓存的文献
 */
export async function getCachedPapers(): Promise<Paper[]> {
  return (await getStorageItem(STORAGE_KEYS.CACHED_PAPERS)) || [];
}

/**
 * 设置缓存的文献
 */
export async function setCachedPapers(papers: Paper[]): Promise<void> {
  return setStorageItem(STORAGE_KEYS.CACHED_PAPERS, papers);
}

/**
 * 清除缓存的文献
 */
export async function clearCachedPapers(): Promise<void> {
  return removeStorageItem(STORAGE_KEYS.CACHED_PAPERS);
}

/**
 * 获取AI配置
 */
export async function getAIConfigs(): Promise<UserAIConfig[]> {
  return (await getStorageItem(STORAGE_KEYS.AI_CONFIGS)) || [];
}

/**
 * 设置AI配置
 */
export async function setAIConfigs(configs: UserAIConfig[]): Promise<void> {
  return setStorageItem(STORAGE_KEYS.AI_CONFIGS, configs);
}

/**
 * 用户是否已登录
 */
export async function isLoggedIn(): Promise<boolean> {
  const token = await getAccessToken();
  return !!token;
}

/**
 * 清除登录状态
 */
export async function clearAuth(): Promise<void> {
  await chrome.storage.local.remove([
    STORAGE_KEYS.ACCESS_TOKEN,
    STORAGE_KEYS.REFRESH_TOKEN,
    STORAGE_KEYS.USER
  ]);
}
