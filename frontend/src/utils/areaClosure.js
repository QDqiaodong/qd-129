/**
 * 阅览区“今日闭馆”挂牌的判定与展示工具。
 * 闭馆是一张带结束时刻的临时牌：closedUntil 晚于当前时刻才算闭馆中；
 * 到期后字段仍在数据里，但各处（占座开批下拉、容量看板）都应视为已恢复开放。
 */

/**
 * @param {{closedUntil?: string}|null|undefined} area 阅览区
 * @param {Date} [now] 注入当前时间，便于测试
 * @returns {boolean}
 */
export function isAreaClosed(area, now = new Date()) {
  if (!area || !area.closedUntil) return false
  const end = new Date(area.closedUntil)
  if (Number.isNaN(end.getTime())) return false
  return end.getTime() > now.getTime()
}

/**
 * 闭馆结束时刻展示文本，如 今日 18:30 / 明日 09:00 / 09-13 09:00
 * @param {string} closedUntil
 * @param {Date} [now]
 * @returns {string}
 */
export function formatClosedUntil(closedUntil, now = new Date()) {
  if (!closedUntil) return ''
  const end = new Date(closedUntil)
  if (Number.isNaN(end.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  const hm = `${pad(end.getHours())}:${pad(end.getMinutes())}`
  const dayDiff = Math.round(
    (new Date(end.getFullYear(), end.getMonth(), end.getDate()).getTime()
      - new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()) / 86400000
  )
  if (dayDiff === 0) return `今日 ${hm}`
  if (dayDiff === 1) return `次日 ${hm}`
  if (dayDiff === -1) return `昨日 ${hm}`
  return `${pad(end.getMonth() + 1)}-${pad(end.getDate())} ${hm}`
}

/**
 * 分区下拉/卡片上挂在名称后面的闭馆提示角标，未闭馆返回空串
 * @param {{closedUntil?: string}|null|undefined} area
 * @param {Date} [now]
 * @returns {string}
 */
export function closedAreaLabel(area, now = new Date()) {
  if (!isAreaClosed(area, now)) return ''
  return `今日闭馆至 ${closedUntilShort(area.closedUntil, now)}`
}

/**
 * 结束时刻短文案：今天只给时刻（21:00），跨日保留“次日/日期”前缀
 * @param {string} closedUntil
 * @param {Date} [now]
 * @returns {string}
 */
export function closedUntilShort(closedUntil, now = new Date()) {
  return formatClosedUntil(closedUntil, now).replace('今日 ', '')
}
