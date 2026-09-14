/**
 * 夜间巡检逐件登记的判定工具。
 * 与后端 NightInspectionServiceImpl 的硬约束保持一致：
 * 灯、插座、桌面三项结果都必填（没写灯或插座结果不能提交该件），
 * 任一项异常即为“有问题”，有问题必须写处理意见。
 */

export const RESULT_NORMAL = 'NORMAL'
export const RESULT_ABNORMAL = 'ABNORMAL'

/**
 * 三个部位是否任一异常
 * @param {{lightResult?: string, socketResult?: string, deskSurfaceResult?: string}} entry
 * @returns {boolean}
 */
export function hasInspectionProblem(entry) {
  if (!entry) return false
  return entry.lightResult === RESULT_ABNORMAL
    || entry.socketResult === RESULT_ABNORMAL
    || entry.deskSurfaceResult === RESULT_ABNORMAL
}

/**
 * 提交单件前的校验，返回错误文案；可以提交时返回 null
 * @param {{lightResult?: string, socketResult?: string, deskSurfaceResult?: string, handleOpinion?: string}} entry
 * @returns {string|null}
 */
export function validateInspectionEntry(entry) {
  const form = entry || {}
  if (!form.lightResult) return '请先登记灯巡检结果（正常/不亮），没写灯结果不能提交该件'
  if (!form.socketResult) return '请先登记插座巡检结果（正常/失灵），没写插座结果不能提交该件'
  if (!form.deskSurfaceResult) return '请先登记桌面巡检结果（正常/涂鸦破损）'
  if (hasInspectionProblem(form) && !(form.handleOpinion || '').trim()) {
    return '该件存在灯/插座/桌面问题，请填写处理意见后再提交'
  }
  return null
}

/**
 * 批次是否可结束：全部明细都已巡
 * @param {{totalCount?: number, checkedCount?: number}|null} batch
 * @returns {boolean}
 */
export function canCompleteBatch(batch) {
  if (!batch) return false
  return (batch.totalCount || 0) > 0 && batch.checkedCount === batch.totalCount
}

/**
 * 部位结果展示文案
 * @param {'light'|'socket'|'deskSurface'} part
 * @param {string} result NORMAL/ABNORMAL/空
 * @returns {string}
 */
export function resultText(part, result) {
  if (!result) return '未登记'
  const abnormal = { light: '不亮', socket: '失灵', deskSurface: '涂鸦/破损' }
  return result === RESULT_NORMAL ? '正常' : (abnormal[part] || '异常')
}
