/**
 * 批量调区预览结果的失效资产汇总：
 * 按原因码逐项统计，供界面生成与提交数量一致的提示。
 */

export const REASON_NOT_FOUND = 'NOT_FOUND'
export const REASON_DISABLED = 'DISABLED'
export const REASON_ALREADY_IN_TARGET = 'ALREADY_IN_TARGET'

const REASON_TEXT = {
  [REASON_NOT_FOUND]: '资产已删除',
  [REASON_DISABLED]: '资产已停用',
  [REASON_ALREADY_IN_TARGET]: '已在目标分区'
}

export function summarizeInvalidItems(items) {
  const summary = {
    invalidCount: 0,
    notFoundCount: 0,
    disabledCount: 0,
    alreadyInTargetCount: 0
  }
  ;(items || []).forEach(item => {
    if (item && item.valid === false) {
      summary.invalidCount += 1
      if (item.reasonCode === REASON_NOT_FOUND) {
        summary.notFoundCount += 1
      } else if (item.reasonCode === REASON_DISABLED) {
        summary.disabledCount += 1
      } else if (item.reasonCode === REASON_ALREADY_IN_TARGET) {
        summary.alreadyInTargetCount += 1
      }
    }
  })
  return summary
}

/**
 * 生成预览区红色提示标题：
 * 分别列出已删除、已停用、已在目标分区的逐项数量，整批不可提交时提示调整勾选。
 */
export function buildInvalidAlertTitle(summary) {
  const parts = []
  if (summary.notFoundCount > 0) {
    parts.push(`资产已删除 ${summary.notFoundCount} 项`)
  }
  if (summary.disabledCount > 0) {
    parts.push(`资产已停用 ${summary.disabledCount} 项`)
  }
  if (summary.alreadyInTargetCount > 0) {
    parts.push(`已在目标分区 ${summary.alreadyInTargetCount} 项`)
  }
  const detail = parts.length > 0 ? `（${parts.join('，')}）` : ''
  return `有 ${summary.invalidCount} 项不可迁移${detail}，整批不可提交，请调整勾选`
}

export function reasonText(reasonCode) {
  return REASON_TEXT[reasonCode] || '资产不可迁移'
}
