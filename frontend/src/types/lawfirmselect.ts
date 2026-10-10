/** 律所选聘记录（与后端 LawFirmSelectVO 逐字段镜像，id 为雪花 ID 字符串） */
export interface LawFirmSelectVO {
  /** 律所选聘 ID（雪花 ID 字符串） */
  id: string
  /** 标题 */
  title: string
  /** 选聘金额（元，JSON number，最多两位小数，如 120000.5） */
  amount: number
  /** 选聘原因 */
  reason: string
  /** 创建时间（yyyy-MM-dd HH:mm:ss） */
  createTime: string
}

/** 律所选聘新增/修改请求（与后端 LawFirmSelectSaveDTO 镜像，全字段可改） */
export interface LawFirmSelectSaveDTO {
  /** 标题（1~128 位） */
  title: string
  /** 选聘金额（元，>0，整数不超 10 位、小数不超 2 位） */
  amount: number
  /** 选聘原因（1~512 位） */
  reason: string
}
