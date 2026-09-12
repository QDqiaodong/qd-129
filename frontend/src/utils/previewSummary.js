/**
 * 批量调区预览结果的失效资产汇总：
 * 按原因码逐项统计，供界面生成与提交数量一致的提示。
 */

export const REASON_NOT_FOUND = 'NOT_FOUND'
export const REASON_DISABLED = 'DISABLED'
export const REASON_SEAT_HOLDING = 'SEAT_HOLDING'
export const REASON_ALREADY_IN_TARGET = 'ALREADY_IN_TARGET'

const REASON_TEXT = {
  [REASON_NOT_FOUND]: '资产已删除',
  [REASON_DISABLED]: '资产已停用',
  [REASON_SEAT_HOLDING]: '进行中占座',
  [REASON_ALREADY_IN_TARGET]: '已在目标分区'
}

export function summarizeInvalidItems(items) {
  const summary = {
    invalidCount: 0,
    notFoundCount: 0,
    disabledCount: 0,
    seatHoldingCount: 0,
    alreadyInTargetCount: 0,
    // 按进行中占座批次号归并，提示里直接写明是哪一批占座
    seatHoldingByBatch: {}
  }
  ;(items || []).forEach(item => {
    if (item && item.valid === false) {
      summary.invalidCount += 1
      if (item.reasonCode === REASON_NOT_FOUND) {
        summary.notFoundCount += 1
      } else if (item.reasonCode === REASON_DISABLED) {
        summary.disabledCount += 1
      } else if (item.reasonCode === REASON_SEAT_HOLDING) {
        summary.seatHoldingCount += 1
        // 后端异常缺批次号时归入“未知批次”，不能静默吞掉
        const batchNo = item.seatHoldBatchNo || item.errorMessage?.match(/批次\s*([A-Za-z0-9]+)/)?.[1] || '未知批次'
        summary.seatHoldingByBatch[batchNo] = (summary.seatHoldingByBatch[batchNo] || 0) + 1
      } else if (item.reasonCode === REASON_ALREADY_IN_TARGET) {
        summary.alreadyInTargetCount += 1
      }
    }
  })
  return summary
}

/**
 * 生成预览区红色提示标题：
 * 分别列出已删除、档案停用、进行中占座（带批次号与逐项数）、已在目标分区的数量，
 * 整批不可提交时提示调整勾选或去占座批次释放。
 */
export function buildInvalidAlertTitle(summary) {
  const parts = []
  if (summary.notFoundCount > 0) {
    parts.push(`资产已删除 ${summary.notFoundCount} 项`)
  }
  if (summary.disabledCount > 0) {
    parts.push(`资产已停用 ${summary.disabledCount} 项`)
  }
  if (summary.seatHoldingCount > 0) {
    const batchParts = Object.keys(summary.seatHoldingByBatch)
      .map(batchNo => `${holdBatchLabel(batchNo)} ${summary.seatHoldingByBatch[batchNo]} 项`)
    parts.push(`进行中占座批次占住 ${summary.seatHoldingCount} 项（${batchParts.join('，')}）`)
  }
  if (summary.alreadyInTargetCount > 0) {
    parts.push(`已在目标分区 ${summary.alreadyInTargetCount} 项`)
  }
  const detail = parts.length > 0 ? `（${parts.join('，')}）` : ''
  const suffix = summary.seatHoldingCount > 0
    ? '，整批不可提交，请先到对应占座批次释放或调整勾选'
    : '，整批不可提交，请调整勾选'
  return `有 ${summary.invalidCount} 项不可迁移${detail}${suffix}`
}

function holdBatchLabel(batchNo) {
  return batchNo === '未知批次' ? batchNo : `批次 ${batchNo}`
}

export function reasonText(reasonCode) {
  return REASON_TEXT[reasonCode] || '资产不可迁移'
}

/**
 * 刷新后核对预览里的“进行中占座”原因与最新占座列表是否一致。
 * activeHoldMap 由 peakSlot.indexActiveHolds(/seat-hold/active) 生成，
 * 与后端批量校验同口径（仅 OPEN 批次、在占/超时未到）。
 *
 * 对不齐的情况逐项返回明确提示，调用方据此红色告警并阻止提交：
 * - LIST_ONLY：占座列表已在占，但预览未标记占座原因（期间新开了占座批次）
 * - STALE_BATCH：预览标记的批次号与占座列表当前批次不一致（释放后又被另一批占住）
 * - PREVIEW_ONLY：预览标记了占座但列表已无该资产（占座已释放，需要重新预览）
 *
 * @param {Array} previewItems 预览返回的 items
 * @param {Map<number, {batchNo: string, itemStatus: string}>} activeHoldMap
 * @returns {{ consistent: boolean, mismatches: Array<{type: string, deskChairId: *, assetCode: *, message: string}> }}
 */
export function checkHoldConsistency(previewItems, activeHoldMap) {
  const mismatches = []
  const map = activeHoldMap instanceof Map ? activeHoldMap : new Map()

  ;(previewItems || []).forEach(item => {
    if (!item || item.deskChairId == null) return
    const code = item.assetCode || `#${item.deskChairId}`
    const inList = map.get(item.deskChairId)

    if (item.valid === false && item.reasonCode === REASON_SEAT_HOLDING) {
      if (!inList) {
        mismatches.push({
          type: 'PREVIEW_ONLY',
          deskChairId: item.deskChairId,
          assetCode: item.assetCode,
          message: `${code} 预览时被占座批次 ${item.seatHoldBatchNo || ''} 占住，但当前占座列表已无该在占记录（可能已释放），请重新预览`
        })
      } else if (item.seatHoldBatchNo && inList.batchNo !== item.seatHoldBatchNo) {
        mismatches.push({
          type: 'STALE_BATCH',
          deskChairId: item.deskChairId,
          assetCode: item.assetCode,
          message: `${code} 当前被进行中批次 ${inList.batchNo} 占住，与预览标记的批次 ${item.seatHoldBatchNo} 不一致，请重新预览`
        })
      }
    } else if (inList) {
      // 预览认为可迁移/档案停用，但占座列表显示它正被进行中批次占住
      mismatches.push({
        type: 'LIST_ONLY',
        deskChairId: item.deskChairId,
        assetCode: item.assetCode,
        message: `${code} 已被进行中占座批次 ${inList.batchNo} 占住，但预览未标出该占座原因，请重新预览后再提交`
      })
    }
  })

  return { consistent: mismatches.length === 0, mismatches }
}
