/**
 * NDJSON (Newline Delimited JSON) 解析器
 * WOS API 返回的是多行JSON格式，每行是一个独立的JSON对象
 */

/**
 * 解析NDJSON字符串为多行JSON对象数组
 * @param ndjson NDJSON格式字符串
 * @returns 解析后的对象数组
 */
export function parseNDJSON<T = unknown>(ndjson: string): T[] {
  if (!ndjson || typeof ndjson !== 'string') {
    return [];
  }

  return ndjson
    .split('\n')
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .map(line => {
      try {
        return JSON.parse(line) as T;
      } catch {
        console.warn('[PaperPilot] Failed to parse NDJSON line:', line.substring(0, 100));
        return null;
      }
    })
    .filter((item): item is T => item !== null);
}

/**
 * 将对象数组序列化为NDJSON字符串
 * @param items 对象数组
 * @returns NDJSON格式字符串
 */
export function stringifyNDJSON<T = unknown>(items: T[]): string {
  return items.map(item => JSON.stringify(item)).join('\n');
}

/**
 * 流式解析NDJSON (用于处理大文件)
 */
export class NDJSONStreamParser<T = unknown> {
  private buffer = '';

  /**
   * 追加数据到缓冲区并解析
   * @param chunk 新数据块
   * @returns 解析出的对象数组
   */
  parse(chunk: string): T[] {
    this.buffer += chunk;
    const lines = this.buffer.split('\n');

    // 保留最后一行（可能不完整）
    this.buffer = lines.pop() || '';

    return lines
      .map(line => line.trim())
      .filter(line => line.length > 0)
      .map(line => {
        try {
          return JSON.parse(line) as T;
        } catch {
          return null;
        }
      })
      .filter((item): item is T => item !== null);
  }

  /**
   * 刷新缓冲区，解析剩余内容
   */
  flush(): T | null {
    if (this.buffer.trim()) {
      try {
        return JSON.parse(this.buffer.trim()) as T;
      } catch {
        return null;
      } finally {
        this.buffer = '';
      }
    }
    return null;
  }

  /**
   * 重置解析器
   */
  reset(): void {
    this.buffer = '';
  }
}
