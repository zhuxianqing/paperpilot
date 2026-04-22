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

/** 分页结果 */
export interface PaginatedResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}
