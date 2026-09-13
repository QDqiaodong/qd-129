/**
 * 高峰占座时段预设与占座勾选可用性判定
 */

export const PEAK_TIME_SLOTS = [
  { value: '08:00-11:30 早高峰', label: '早高峰 08:00-11:30' },
  { value: '12:00-13:30 午间高峰', label: '午间高峰 12:00-13:30' },
  { value: '14:00-17:30 下午高峰', label: '下午高峰 14:00-17:30' },
  { value: '18:30-21:30 晚间高峰', label: '晚间高峰 18:30-21:30' },
  { value: '09:00-21:00 周末全天', label: '周末全天 09:00-21:00' }
]

/**
 * 汇总进行中批次正在占住（含超时未到）的资产，按 deskChairId 索引，
 * 供勾选列表禁选与"同一在占资产再开一批"的明确提示。
 * @param {Array} activeHolds /seat-hold/active 返回的明细列表
 * @returns {Map<number, {batchNo: string, itemStatus: string}>}
 */
export function indexActiveHolds(activeHolds) {
  const map = new Map()
  ;(activeHolds || []).forEach(item => {
    if (item && item.deskChairId != null && !map.has(item.deskChairId)) {
      map.set(item.deskChairId, {
        batchNo: item.batchNo,
        itemStatus: item.itemStatus
      })
    }
  })
  return map
}

/**
 * 汇总分区内仍待领取的遗失物品，按 deskChairId 索引，
 * 供占座勾选列表禁选并给出遗失单号提示。
 * @param {Array} pendingItems /lost-item/pending 返回的待领取列表
 * @returns {Map<number, {itemNo: string, itemName: string}>}
 */
export function indexPendingLostItems(pendingItems) {
  const map = new Map()
  ;(pendingItems || []).forEach(item => {
    if (item && item.deskChairId != null && !map.has(item.deskChairId)) {
      map.set(item.deskChairId, {
        itemNo: item.itemNo,
        itemName: item.itemName
      })
    }
  })
  return map
}

/**
 * 资产能否在当前批次勾选占住：
 * - 必须属于开批分区
 * - 桌椅档案为可用（status === 1）
 * - 未被其他进行中批次占住（含超时未到）
 * - 桌椅旁没有仍待领取的遗失物品（领取闭环前不能开高峰占座）
 * - 追加占住时，未在当前批次已有明细中（含已释放，一件资产一批只占一次）
 * @param {Set<number>} [existingInBatch] 当前批次已有明细的资产 ID 集合
 * @param {Map<number, {itemNo: string, itemName: string}>} [pendingLostMap] 待领取遗失物品索引
 * @returns {{ selectable: boolean, reason: string|null }}
 */
export function holdEligibility(desk, areaId, activeHoldMap, existingInBatch, pendingLostMap) {
  if (!desk || desk.id == null) {
    return { selectable: false, reason: '资产不存在' }
  }
  if (areaId != null && desk.areaId !== areaId) {
    return { selectable: false, reason: '不属于所选分区' }
  }
  if (existingInBatch instanceof Set && existingInBatch.has(desk.id)) {
    return { selectable: false, reason: '已在本批次中，同一批次不能重复占住，如需再占请另开新批' }
  }
  const active = activeHoldMap instanceof Map ? activeHoldMap.get(desk.id) : null
  if (active) {
    const statusText = active.itemStatus === 'TIMEOUT' ? '超时未到' : '在占'
    return {
      selectable: false,
      reason: `已被进行中批次 ${active.batchNo} 占住（${statusText}），请先释放后再开批`
    }
  }
  const lost = pendingLostMap instanceof Map ? pendingLostMap.get(desk.id) : null
  if (lost) {
    return {
      selectable: false,
      reason: `桌椅旁有待领取遗失物品（${lost.itemNo} ${lost.itemName}），领取闭环前不能开高峰占座`
    }
  }
  if (Number(desk.status) === 0) {
    return { selectable: false, reason: '资产停用中（可能在报修），不能占座' }
  }
  return { selectable: true, reason: null }
}

export function itemStatusText(status) {
  return {
    HOLDING: '在占',
    TIMEOUT: '超时未到',
    RELEASED: '已释放'
  }[status] || status
}

export function batchStatusText(status) {
  return {
    OPEN: '进行中',
    ENDED: '已结束'
  }[status] || status
}

/**
 * 用一次处置（释放/改超时/撤回/结束/追加/清场）后接口返回的最新批次，
 * 原地同步批次列表里对应行的计数与状态。
 *
 * 必须在处置成功后立即执行：抽屉操作后批次列表位于抽屉下层，二次列表请求若因
 * 任何原因（网关/浏览器缓存、接口抖动等）拿到旧值，值班员看到的"在占/超时/已释放"
 * 就会停在点开前的数字。直接采用处置接口返回的权威结果可以保证计数立刻变化。
 *
 * 不重新筛选、不重置筛选条件：进行中等状态筛选由调用方继续保留，列表里不存在的
 * 批次（例如刚结束导致不再命中"进行中"）不强行插入，保持当前筛选结果口径。
 *
 * @param {Array} batches 当前批次列表（不会被改写，返回新数组）
 * @param {Object} updated 处置接口返回的最新批次
 * @returns {Array} 同步计数后的新批次列表
 */
export function mergeBatchIntoList(batches, updated) {
  if (!updated || updated.id == null) {
    return Array.isArray(batches) ? batches : []
  }
  const list = Array.isArray(batches) ? batches : []
  let changed = false
  const next = list.map(row => {
    if (!row || row.id !== updated.id) {
      return row
    }
    changed = true
    // 处置接口只返回批次本身（不含分区名等列表联表字段），合并时保留列表行已有的冗余字段
    const { areaName, areaCode, ...rest } = updated
    return {
      ...row,
      ...rest,
      areaName: areaName ?? row.areaName,
      areaCode: areaCode ?? row.areaCode
    }
  })
  // 行不在当前筛选结果里（如已结束掉出"进行中"），说明它本就不该出现，保持列表不变
  return changed ? next : list
}
