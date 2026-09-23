/**
 * 文件识别（版面解析）相关类型。
 * 后端 ApiResponse.data 即上游 result 原始 JSON，字段名与上游契约（接口文档 §3）完全一致，不做重命名。
 * 注意：block_bbox 坐标系为上游栅格化后的页面像素（PDF 按 144dpi 换算，图像即原图像素）。
 */

/** 文件类型：0 = PDF，1 = 图像 */
export type OcrFileType = 0 | 1

/** 版面解析块（上游 parsing_res_list 元素，字段名沿用上游） */
export interface OcrParsingBlock {
  /** 版面区域边界框（结果图像素坐标 [x1, y1, x2, y2]） */
  block_bbox: number[] | null
  /** 版面区域标签：text/title/table/figure/formula/seal 等 */
  block_label: string
  /** 识别内容（文本/表格 HTML/公式） */
  block_content: string
  /** 版面区域索引 */
  block_id: number
  /** 阅读顺序编号，非排序部分为 null */
  block_order: number | null
}

/** 单页结构化解析结果（上游 prunedResult） */
export interface OcrPrunedResult {
  /** 解析结果列表，顺序即阅读顺序 */
  parsing_res_list: OcrParsingBlock[] | null
}

/** Markdown 解析结果（上游 markdown） */
export interface OcrMarkdown {
  text: string | null
  /** 图片相对路径 → 图像内容（Base64 或 URL） */
  images: Record<string, string> | null
}

/** 单页版面解析结果（上游 layoutParsingResults 元素） */
export interface OcrLayoutPage {
  prunedResult: OcrPrunedResult | null
  markdown: OcrMarkdown | null
  /** 可视化结果图键值对（JPEG Base64 或 URL），visualize=true 时返回（本项目固定 false，恒为 null） */
  outputImages: Record<string, string> | null
  /** 输入图像（JPEG Base64 或 URL），visualize=true 时返回（本项目固定 false，恒为 null） */
  inputImage: string | null
}

/** 上游 result 原始 JSON（后端 ApiResponse.data 原样透传） */
export interface OcrRecognizeResult {
  /** 图像输入长度为 1，PDF 输入为实际处理页数，顺序对应页序 */
  layoutParsingResults: OcrLayoutPage[] | null
  /** 输入数据信息 */
  dataInfo: Record<string, unknown> | null
}

/** 上传弹框确认载荷 */
export interface OcrUploadPayload {
  file: File
  fileType: OcrFileType
}
