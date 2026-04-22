/**
 * 文本格式化工具函数
 */

/**
 * 截断文本
 * @param text 原文本
 * @param maxLength 最大长度
 * @param suffix 后缀，默认为"..."
 * @returns 截断后的文本
 */
export function truncate(text: string | undefined | null, maxLength: number, suffix = '...'): string {
  if (!text) return '';
  if (text.length <= maxLength) return text;
  return text.substring(0, maxLength - suffix.length) + suffix;
}

/**
 * 格式化作者列表
 * @param authors 作者数组
 * @param maxAuthors 最多显示作者数
 * @returns 格式化后的作者字符串
 */
export function formatAuthors(authors: string[] | undefined, maxAuthors = 3): string {
  if (!authors || authors.length === 0) return 'Unknown';
  if (authors.length <= maxAuthors) return authors.join(', ');
  return `${authors.slice(0, maxAuthors).join(', ')} et al.`;
}

/**
 * 格式化数字（添加千位分隔符）
 * @param num 数字
 * @returns 格式化后的字符串
 */
export function formatNumber(num: number | undefined): string {
  if (num === undefined || num === null) return '-';
  return num.toLocaleString('zh-CN');
}

/**
 * 格式化日期
 * @param date 日期字符串或Date对象
 * @returns 格式化后的日期字符串
 */
export function formatDate(date: string | Date | undefined): string {
  if (!date) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
}

/**
 * 格式化日期时间
 * @param date 日期字符串或Date对象
 * @returns 格式化后的日期时间字符串
 */
export function formatDateTime(date: string | Date | undefined): string {
  if (!date) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}

/**
 * 获取分区的样式类
 * @param quartile 分区 Q1/Q2/Q3/Q4
 * @returns CSS类名
 */
export function getQuartileClass(quartile?: string): string {
  switch (quartile) {
    case 'Q1': return 'badge quartile-q1';
    case 'Q2': return 'badge quartile-q2';
    case 'Q3': return 'badge quartile-q3';
    case 'Q4': return 'badge quartile-q4';
    default: return 'badge bg-gray-100 text-gray-600';
  }
}

/**
 * 安全地解析JSON
 * @param json JSON字符串
 * @param defaultValue 解析失败时的默认值
 * @returns 解析结果或默认值
 */
export function safeJSONParse<T>(json: string, defaultValue: T): T {
  try {
    return JSON.parse(json) as T;
  } catch {
    return defaultValue;
  }
}

/**
 * 防抖函数
 * @param fn 原函数
 * @param delay 延迟时间（毫秒）
 * @returns 防抖后的函数
 */
export function debounce<T extends (...args: unknown[]) => unknown>(
  fn: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  return function (this: unknown, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}

/**
 * 节流函数
 * @param fn 原函数
 * @param limit 限制时间（毫秒）
 * @returns 节流后的函数
 */
export function throttle<T extends (...args: unknown[]) => unknown>(
  fn: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle = false;
  return function (this: unknown, ...args: Parameters<T>) {
    if (!inThrottle) {
      fn.apply(this, args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  };
}
