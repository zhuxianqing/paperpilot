/**
 * WOS 数据解析器
 * 将 WOS API 返回的 NDJSON 数据解析为内部 Paper 格式
 */

import type { Paper, WOSApiRecord, WOSApiResponse } from '../shared/types/paper';
import { parseNDJSON } from '../shared/utils/ndjson';

/**
 * 解析 WOS 数据
 * @param data WOS API 返回的 NDJSON 字符串
 * @returns Paper 数组
 */
export function parseWOSData(data: string): Paper[] {
  if (!data || typeof data !== 'string') {
    console.warn('[PaperPilot Parser] Empty or invalid data');
    return [];
  }

  try {
    // 解析 NDJSON
    const responses = parseNDJSON<WOSApiResponse>(data);
    const papers: Paper[] = [];

    for (const response of responses) {
      // 只处理 records 类型的响应
      if (response?.key !== 'records' || !response?.payload) {
        continue;
      }

      // 遍历所有记录
      for (const [recordKey, record] of Object.entries(response.payload)) {
        const paper = convertToPaper(record, recordKey);
        if (paper) {
          papers.push(paper);
        }
      }
    }

    console.log(`[PaperPilot Parser] Parsed ${papers.length} papers`);
    return papers;
  } catch (error) {
    console.error('[PaperPilot Parser] Error parsing WOS data:', error);
    return [];
  }
}

/**
 * 将 WOS 原始记录转换为内部 Paper 格式
 * @param raw WOS 原始记录
 * @returns Paper 对象
 */
function convertToPaper(raw: WOSApiRecord, recordKey?: string): Paper | null {
  if (!raw) return null;

  try {
    // 提取标题
    const title = extractTitle(raw);

    // 提取作者
    const authors = extractAuthors(raw);

    // 提取来源（期刊）
    const journal = extractJournal(raw);

    // 提取年份
    const publishYear = extractPublishYear(raw);

    // 提取 DOI
    const doi = extractDoi(raw);

    // 提取摘要
    const abstract = extractAbstract(raw);

    // 提取被引次数
    const citations = extractCitations(raw);

    // 构建原文链接
    const sourceUrl = buildSourceUrl(resolveRecordUid(raw, recordKey));

    return {
      doi,
      title,
      authors,
      abstract,
      journal,
      publishYear,
      citations,
      sourceUrl,
      selected: true
    };
  } catch (error) {
    console.error('[PaperPilot Parser] Error converting record:', error);
    return null;
  }
}

/**
 * 提取标题
 */
function extractTitle(raw: WOSApiRecord): string {
  try {
    const titles = raw.titles?.item?.en;
    if (titles && titles.length > 0) {
      return titles[0].title?.trim() || 'Unknown Title';
    }
    return 'Unknown Title';
  } catch {
    return 'Unknown Title';
  }
}

/**
 * 提取作者列表
 */
function extractAuthors(raw: WOSApiRecord): string[] {
  try {
    const authors = raw.names?.author?.en;
    if (!authors || !Array.isArray(authors)) {
      return [];
    }

    return authors
      .map(author => {
        // 使用 wos_standard 格式（如 "Chen, JK"）或组合 first_name + last_name
        if (author.wos_standard) {
          return author.wos_standard;
        }
        if (author.last_name || author.first_name) {
          const last = author.last_name || '';
          const first = author.first_name || '';
          const initial = first.charAt(0).toUpperCase();
          return `${last}${first ? `, ${initial}` : ''}`;
        }
        return '';
      })
      .filter(name => name.length > 0);
  } catch {
    return [];
  }
}

/**
 * 提取期刊名
 */
function extractJournal(raw: WOSApiRecord): string | undefined {
  try {
    const source = raw.source?.en;
    if (source && source.length > 0) {
      return source[0].title?.trim();
    }
    return undefined;
  } catch {
    return undefined;
  }
}

/**
 * 提取发表年份
 */
function extractPublishYear(raw: WOSApiRecord): number | undefined {
  try {
    // 优先使用 pubyear
    if (raw.pub_info?.pubyear) {
      return raw.pub_info.pubyear;
    }

    // 从 sortdate 提取（如 "2025-12-24"）
    if (raw.pub_info?.sortdate) {
      const year = parseInt(raw.pub_info.sortdate.split('-')[0], 10);
      if (!isNaN(year)) {
        return year;
      }
    }

    return undefined;
  } catch {
    return undefined;
  }
}

/**
 * 提取 DOI
 */
function extractDoi(raw: WOSApiRecord): string | undefined {
  try {
    if (raw.doi) {
      return raw.doi;
    }

    // 从 identifiers 中查找
    if (raw.identifiers && Array.isArray(raw.identifiers)) {
      const doiId = raw.identifiers.find(id => id.type === 'doi');
      return doiId?.value;
    }

    return undefined;
  } catch {
    return undefined;
  }
}

/**
 * 提取摘要
 */
function extractAbstract(raw: WOSApiRecord): string | undefined {
  try {
    return raw.abstract?.basic?.en?.abstract;
  } catch {
    return undefined;
  }
}

/**
 * 提取被引次数
 */
function extractCitations(raw: WOSApiRecord): number {
  try {
    const counts = raw.citation_related?.counts;
    if (counts?.WOSCC) {
      return counts.WOSCC;
    }
    if (counts?.ALLDB) {
      return counts.ALLDB;
    }
    return 0;
  } catch {
    return 0;
  }
}

/**
 * 解析文献 UID
 */
function resolveRecordUid(raw: WOSApiRecord, recordKey?: string): string {
  if (raw.uid) return raw.uid;
  if (recordKey) return recordKey;
  if (raw.docid) return raw.docid;
  return '';
}

/**
 * 构建原文链接
 */
function buildSourceUrl(uid: string): string {
  if (!uid) return '';
  return `https://www.webofscience.com/wos/woscc/full-record/${uid}`;
}

/**
 * 验证 Paper 数据完整性
 */
export function validatePaper(paper: Paper): boolean {
  if (!paper) return false;
  if (!paper.title || paper.title.trim().length === 0) return false;
  return true;
}

/**
 * 过滤和清理 Paper 列表
 */
export function sanitizePapers(papers: Paper[]): Paper[] {
  return papers
    .filter(validatePaper)
    .map((paper, index) => ({
      ...paper,
      id: index + 1
    }));
}

/**
 * 从页面 DOM 中提取文献（已废弃 - 不再使用）
 * @deprecated API 拦截是唯一数据来源，此函数保留但不会自动调用
 */
export function extractPapersFromDOM(): Paper[] {
  console.warn('[PaperPilot Parser] extractPapersFromDOM is deprecated and should not be called');
  return [];
}