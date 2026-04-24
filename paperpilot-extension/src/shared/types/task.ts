import type { Paper } from './paper';

/** 搜索任务 */
export interface SearchTask {
  /** 任务编号 */
  taskNo: string;
  /** 来源 */
  source: 'wos' | 'sciencedirect' | 'semanticscholar';
  /** 搜索关键词 */
  keyword: string;
  /** 任务状态 */
  status: 'pending' | 'running' | 'completed' | 'failed';
  /** 总文献数 */
  totalCount: number;
  /** 已处理数 */
  processedCount: number;
  /** 结果文件URL */
  resultFileUrl?: string;
  /** 创建时间 */
  createdAt: string;
  /** 错误信息 */
  errorMessage?: string;
}

/** 创建任务请求 */
export interface CreateTaskRequest {
  source: 'wos' | 'sciencedirect' | 'semanticscholar';
  keyword: string;
  yearFrom?: number;
  yearTo?: number;
  quartiles?: ('Q1' | 'Q2' | 'Q3' | 'Q4')[];
  minImpactFactor?: number;
  useAI: boolean;
  maxResults?: number;
}

/** AI分析请求 */
export interface AnalyzeRequest {
  papers: Paper[];
  useUserConfig?: boolean;
}

/** 导出请求 */
export interface ExportRequest {
  papers: Paper[];
}

/** Popup 本地分析任务（旧） */
export interface AnalysisTask {
  status: 'running' | 'completed' | 'failed';
  paperDOIs: string[];
  startTime: number;
  endTime?: number;
  errorMessage?: string;
}

/** 后端分析任务摘要 */
export interface AnalysisTaskSummary {
  id: number;
  taskNo: string;
  status: 'pending' | 'processing' | 'completed' | 'partial_failed' | 'failed';
  totalCount: number;
  processedCount: number;
  successCount: number;
  failedCount: number;
  newCount?: number;
  reusedCount?: number;
  reanalyzeCount?: number;
  useUserConfig?: boolean;
  provider?: string;
  model?: string;
  quotaReserved?: number;
  quotaConsumed?: number;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

/** 后端单篇分析结果 */
export interface AnalysisPaperResult {
  id: number;
  paperKey: string;
  paperDoi?: string;
  title: string;
  titleCn?: string;
  abstractText?: string;
  abstractCn?: string;
  authors?: string;
  journal?: string;
  publishYear?: number;
  citations?: number;
  sourcePlatform?: string;
  sourceUrl?: string;
  pdfUrl?: string;
  summaryZh?: string;
  keywordsZh?: string;
  methodologyZh?: string;
  conclusionZh?: string;
  researchFindingsZh?: string;
  resultSource?: 'new' | 'reused' | 'reanalyze';
  displayOrder?: number;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  errorMessage?: string;
  analyzedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

/** 分页结果 */
export interface PaginatedResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
