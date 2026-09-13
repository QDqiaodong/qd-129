import { describe, it, expect } from 'vitest'
import { isAreaClosed, formatClosedUntil, closedUntilShort, closedAreaLabel } from '../areaClosure'

describe('阅览区今日闭馆工具', () => {
  const now = new Date(2026, 8, 12, 10, 0) // 2026-09-12 10:00 本地时间

  it('closedUntil 晚于当前时刻才算闭馆中', () => {
    expect(isAreaClosed({ closedUntil: '2026-09-12T18:30:00' }, now)).toBe(true)
    expect(isAreaClosed({ closedUntil: '2026-09-12T10:00:01' }, now)).toBe(true)
    // 刚到期：字段还在，但已不产生闭馆效力
    expect(isAreaClosed({ closedUntil: '2026-09-12T10:00:00' }, now)).toBe(false)
    expect(isAreaClosed({ closedUntil: '2026-09-12T09:00:00' }, now)).toBe(false)
  })

  it('无挂牌、空对象、非法时间都视为开放', () => {
    expect(isAreaClosed(null, now)).toBe(false)
    expect(isAreaClosed({}, now)).toBe(false)
    expect(isAreaClosed({ closedUntil: '' }, now)).toBe(false)
    expect(isAreaClosed({ closedUntil: 'not-a-time' }, now)).toBe(false)
  })

  it('结束时刻按今日/次日/跨日格式化', () => {
    expect(formatClosedUntil('2026-09-12T18:30:00', now)).toBe('今日 18:30')
    expect(formatClosedUntil('2026-09-13T09:00:00', now)).toBe('次日 09:00')
    expect(formatClosedUntil('2026-09-15T08:00:00', now)).toBe('09-15 08:00')
    expect(formatClosedUntil('', now)).toBe('')
  })

  it('closedAreaLabel 给出下拉中使用的闭馆角标', () => {
    expect(closedAreaLabel({ closedUntil: '2026-09-12T21:00:00' }, now)).toBe('今日闭馆至 21:00')
    expect(closedAreaLabel({ closedUntil: '2026-09-13T09:00:00' }, now)).toBe('今日闭馆至 次日 09:00')
    expect(closedAreaLabel({ closedUntil: '2026-09-12T09:00:00' }, now)).toBe('')
    expect(closedAreaLabel(null, now)).toBe('')
  })

  it('closedUntilShort 今天只给时刻，跨日保留日期前缀', () => {
    expect(closedUntilShort('2026-09-12T21:00:00', now)).toBe('21:00')
    expect(closedUntilShort('2026-09-13T09:00:00', now)).toBe('次日 09:00')
  })
})
