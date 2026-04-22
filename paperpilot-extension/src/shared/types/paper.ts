/** 文献信息 */
export interface Paper {
  /** 本地ID */
  id?: number;
  /** DOI */
  doi?: string;
  /** 标题 */
  title: string;
  /** 作者列表 */
  authors: string[];
  /** 摘要 */
  abstract?: string;
  /** 期刊名 */
  journal?: string;
  /** 发表年份 */
  publishYear?: number;
  /** 影响因子 */
  impactFactor?: number;
  /** 分区 Q1/Q2/Q3/Q4 */
  quartile?: 'Q1' | 'Q2' | 'Q3' | 'Q4';
  /** 被引次数 */
  citations?: number;
  /** PDF链接 */
  pdfUrl?: string;
  /** 原文链接 */
  sourceUrl: string;
  /** AI总结 - 一句话核心贡献 */
  aiSummary?: string;
  /** AI提取关键词 */
  aiKeywords?: string[];
  /** 研究方法 */
  methodology?: string;
  /** 研究结论 */
  conclusion?: string;
  /** 研究成果 */
  researchFindings?: string;
  /** 是否被选中 */
  selected?: boolean;
}

/** 文献筛选条件 */
export interface PaperFilters {
  /** 起始年份 */
  yearFrom?: number;
  /** 结束年份 */
  yearTo?: number;
  /** 分区筛选 */
  quartiles?: ('Q1' | 'Q2' | 'Q3' | 'Q4')[];
  /** 最小影响因子 */
  minImpactFactor?: number;
}

/** 导出选项 */
export interface ExportOptions {
  /** 导出格式 */
  format: 'feishu' | 'excel';
  /** 是否仅导出选中项 */
  selectedOnly: boolean;
}

/** WOS API 原始响应 */
export interface WOSApiRecord {
  /** 文档 ID */
  docid: string;
  /** WOS 唯一标识 (UID) */
  uid: string;
  /** DOI */
  doi?: string;
  /** 标题列表 */
  titles?: {
    item?: {
      en?: Array<{ title: string }>;
    };
  };
  /** 作者列表 */
  names?: {
    author?: {
      en?: Array<{
        daisng_id?: string;
        last_name?: string;
        first_name?: string;
        wos_standard?: string;
      }>;
    };
  };
  /** 来源信息 */
  source?: {
    en?: Array<{ title: string }>;
  };
  /** 出版信息 */
  pub_info?: {
    pubyear?: number;
    sortdate?: string;
  };
  /** 摘要 */
  abstract?: {
    basic?: {
      en?: {
        abstract?: string;
      };
    };
  };
  /** 被引次数 */
  citation_related?: {
    counts?: {
      ALLDB?: number;
      WOSCC?: number;
    };
  };
  /** 标识符 */
  identifiers?: Array<{
    type: string;
    value: string;
  }>;
}

/** WOS API 响应结构 */
export interface WOSApiResponse {
  api?: string;
  id?: number;
  key?: string;
  payload?: Record<string, WOSApiRecord>;
}