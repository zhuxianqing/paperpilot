/**
 * WOS 数据解析器
 * 将 WOS API 返回的 NDJSON 数据解析为内部 Paper 格式
 */

import type { Paper, WOSApiRecord, WOSApiResponse } from '../shared/types/paper';
import { parseNDJSON } from '../shared/utils/ndjson';

export function parseWOSData(data: string): Paper[] {
  if (!data || typeof data !== 'string') {
    console.warn('[PaperPilot Parser] Empty or invalid data');
    return [];
  }

  try {
    const responses = parseNDJSON<WOSApiResponse>(data);
    const papers: Paper[] = [];

    for (const response of responses) {
      if (response?.key !== 'records' || !response?.payload) {
        continue;
      }

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

function convertToPaper(raw: WOSApiRecord, recordKey?: string): Paper | null {
  if (!raw) return null;

  try {
    const title = extractTitle(raw);
    const authors = extractAuthors(raw);
    const journal = extractJournal(raw);
    const publishYear = extractPublishYear(raw);
    const doi = extractDoi(raw);
    const abstract = extractAbstract(raw);
    const citations = extractCitations(raw);
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

function extractTitle(raw: WOSApiRecord): string {
  try {
    const titleGroups = raw.titles?.item;
    if (!titleGroups) return 'Unknown Title';
    for (const titles of Object.values(titleGroups)) {
      const title = titles?.find((item) => item?.title)?.title?.trim();
      if (title) return title;
    }
    return 'Unknown Title';
  } catch {
    return 'Unknown Title';
  }
}

function extractAuthors(raw: WOSApiRecord): string[] {
  try {
    const candidates: Array<Record<string, unknown>> = [];
    const authorField = raw.names?.author;
    if (Array.isArray(authorField)) {
      candidates.push(...authorField);
    } else if (authorField && typeof authorField === 'object') {
      for (const value of Object.values(authorField)) {
        if (Array.isArray(value)) {
          candidates.push(...value);
        }
      }
    }

    const staticNames = raw.static_data?.summary?.names?.name;
    if (Array.isArray(staticNames)) {
      candidates.push(...staticNames);
    }

    const normalized = candidates
      .map((author) => {
        const standard = typeof author.wos_standard === 'string' ? author.wos_standard.trim() : '';
        if (standard) return standard;

        const fullName = typeof author.full_name === 'string' ? author.full_name.trim() : '';
        if (fullName) return fullName;

        const displayName = typeof author.display_name === 'string' ? author.display_name.trim() : '';
        if (displayName) return displayName;

        const last = typeof author.last_name === 'string' ? author.last_name.trim() : '';
        const first = typeof author.first_name === 'string' ? author.first_name.trim() : '';
        if (last || first) {
          return [first, last].filter(Boolean).join(' ');
        }
        return '';
      })
      .filter(Boolean);

    return [...new Set(normalized)];
  } catch {
    return [];
  }
}

function extractJournal(raw: WOSApiRecord): string | undefined {
  try {
    const source = raw.source;
    if (!source) return undefined;
    for (const items of Object.values(source)) {
      const title = items?.find((item) => item?.title)?.title?.trim();
      if (title) return title;
    }
    return undefined;
  } catch {
    return undefined;
  }
}

function extractPublishYear(raw: WOSApiRecord): number | undefined {
  try {
    if (raw.pub_info?.pubyear) {
      return raw.pub_info.pubyear;
    }

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

function extractDoi(raw: WOSApiRecord): string | undefined {
  try {
    if (raw.doi) {
      return raw.doi;
    }

    if (raw.identifiers && Array.isArray(raw.identifiers)) {
      const doiId = raw.identifiers.find(id => String(id.type).toLowerCase() === 'doi');
      return doiId?.value;
    }

    return undefined;
  } catch {
    return undefined;
  }
}

function extractAbstract(raw: WOSApiRecord): string | undefined {
  try {
    const directCandidates: unknown[] = [
      raw.abstract?.basic?.en?.abstract,
      raw.abstract?.summary?.en?.abstract,
      ...(raw.abstract?.basic ? Object.values(raw.abstract.basic).map(item => item?.abstract) : []),
      ...(raw.abstract?.summary ? Object.values(raw.abstract.summary).map(item => item?.abstract) : [])
    ];

    for (const candidate of directCandidates) {
      const normalized = normalizeAbstractCandidate(candidate);
      if (normalized) return normalized;
    }

    const paragraphs = raw.abstract?.paragraphs;
    if (Array.isArray(paragraphs)) {
      const text = paragraphs.map((item) => item.text?.trim()).filter(Boolean).join(' ');
      if (text) return text;
    }

    const staticAbstracts = raw.static_data?.summary?.abstracts?.abstract;
    if (Array.isArray(staticAbstracts)) {
      for (const item of staticAbstracts) {
        const text = extractNestedText(item);
        if (text) return text;
      }
    }

    return undefined;
  } catch {
    return undefined;
  }
}

function normalizeAbstractCandidate(candidate: unknown): string | undefined {
  if (typeof candidate === 'string') {
    const trimmed = candidate.trim();
    return trimmed || undefined;
  }
  if (Array.isArray(candidate)) {
    const joined = candidate.filter((item): item is string => typeof item === 'string').map((item) => item.trim()).filter(Boolean).join(' ');
    return joined || undefined;
  }
  return undefined;
}

function extractNestedText(value: unknown): string | undefined {
  if (typeof value === 'string') {
    return value.trim() || undefined;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const text = extractNestedText(item);
      if (text) return text;
    }
    return undefined;
  }
  if (value && typeof value === 'object') {
    for (const nested of Object.values(value)) {
      const text = extractNestedText(nested);
      if (text) return text;
    }
  }
  return undefined;
}

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

function resolveRecordUid(raw: WOSApiRecord, recordKey?: string): string {
  if (raw.uid) return raw.uid;
  if (recordKey) return recordKey;
  if (raw.docid) return raw.docid;
  return '';
}

function buildSourceUrl(uid: string): string {
  if (!uid) return '';
  return `https://www.webofscience.com/wos/woscc/full-record/${uid}`;
}

export function validatePaper(paper: Paper): boolean {
  if (!paper) return false;
  if (!paper.title || paper.title.trim().length === 0) return false;
  return true;
}

export function sanitizePapers(papers: Paper[]): Paper[] {
  return papers
    .filter(validatePaper)
    .map((paper, index) => ({
      ...paper,
      id: index + 1
    }));
}

export function extractPapersFromDOM(): Paper[] {
  console.warn('[PaperPilot Parser] extractPapersFromDOM is deprecated and should not be called');
  return [];
}
