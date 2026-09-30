/**
 * 审批流程配置域类型，与后端 `com.paddleocr.web.model.{dto,vo}` 逐字段镜像。
 * 时间字段为后端全局 LocalDateTime 序列化格式 `yyyy-MM-dd HH:mm:ss` 字符串。
 */

/** 流程状态（后端大写枚举值） */
export type ProcessStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

/** 发起范围 */
export type InitiatorType = 'ALL' | 'SPECIFIC'

/** 流程配置列表行（ProcessVO 镜像） */
export interface ProcessVO {
  id: string
  code: string
  name: string
  status: ProcessStatus
  /** 当前已发布版本号（如 v1），从未发布为 null */
  currentVersion: string | null
  updateTime: string
}

/** 流程详情（ProcessDetailVO 镜像） */
export interface ProcessDetailVO {
  id: string
  code: string
  name: string
  description: string | null
  startPath: string
  detailPath: string
  initiatorType: InitiatorType
  initiatorMembers: string[]
  initiatorRoles: string[]
  status: ProcessStatus
  currentVersion: string | null
}

/** 流程设计查询结果（ProcessDesignVO 镜像） */
export interface ProcessDesignVO {
  /** 查草稿时为草稿所基于的已发布版本；指定版本查询时为该版本号 */
  version: string | null
  /** DRAFT=草稿 PUBLISHED=版本快照 */
  status: 'DRAFT' | 'PUBLISHED'
  /** WorkflowDoc JSON 字符串，从未保存过设计时为 null */
  designJson: string | null
  updateTime: string | null
}

/** 流程版本列表行（ProcessVersionVO 镜像） */
export interface ProcessVersionVO {
  version: string
  updateTime: string
}

/** 流程新增入参（ProcessSaveDTO 镜像） */
export interface ProcessSaveDTO {
  code: string
  name: string
  startPath: string
  detailPath: string
  description?: string
  initiatorType: InitiatorType
  initiatorMembers?: string[]
  initiatorRoles?: string[]
}

/** 流程基本信息更新入参（ProcessUpdateDTO 镜像，code 建后不可改） */
export type ProcessUpdateDTO = Omit<ProcessSaveDTO, 'code'>

/** 流程设计保存入参（ProcessDesignSaveDTO 镜像） */
export interface ProcessDesignSaveDTO {
  designJson: string
}
