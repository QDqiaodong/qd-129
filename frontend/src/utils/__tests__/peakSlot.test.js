import { describe, it, expect } from 'vitest'
import {
  PEAK_TIME_SLOTS,
  indexActiveHolds,
  holdEligibility,
  itemStatusText,
  batchStatusText
} from '../peakSlot'

describe('高峰占座工具函数', () => {
  it('提供常用高峰时段预设且值唯一', () => {
    expect(PEAK_TIME_SLOTS.length).toBeGreaterThanOrEqual(4)
    const values = PEAK_TIME_SLOTS.map(s => s.value)
    expect(new Set(values).size).toBe(values.length)
  })

  it('indexActiveHolds 按资产 ID 索引在占/超时明细', () => {
    const map = indexActiveHolds([
      { deskChairId: 1, batchNo: 'ZZ001', itemStatus: 'HOLDING' },
      { deskChairId: 2, batchNo: 'ZZ002', itemStatus: 'TIMEOUT' },
      { deskChairId: 1, batchNo: 'ZZ009', itemStatus: 'HOLDING' },
      null
    ])
    expect(map.size).toBe(2)
    // 同一资产多条时保留最早批次的提示
    expect(map.get(1).batchNo).toBe('ZZ001')
    expect(map.get(2).itemStatus).toBe('TIMEOUT')
  })

  it('可用且未被占住的本分区资产可以勾选', () => {
    const map = indexActiveHolds([])
    expect(holdEligibility({ id: 1, areaId: 10, status: 1 }, 10, map).selectable).toBe(true)
  })

  it('被进行中批次占住的资产禁选并给出含批次号的明确提示', () => {
    const map = indexActiveHolds([{ deskChairId: 1, batchNo: 'ZZ20260912001', itemStatus: 'HOLDING' }])
    const result = holdEligibility({ id: 1, areaId: 10, status: 0 }, 10, map)
    expect(result.selectable).toBe(false)
    expect(result.reason).toContain('ZZ20260912001')
    expect(result.reason).toContain('在占')

    const timeoutMap = indexActiveHolds([{ deskChairId: 2, batchNo: 'ZZ20260912002', itemStatus: 'TIMEOUT' }])
    expect(holdEligibility({ id: 2, areaId: 10, status: 0 }, 10, timeoutMap).reason).toContain('超时未到')
  })

  it('停用但无在占批次（报修）的资产禁选', () => {
    const result = holdEligibility({ id: 3, areaId: 10, status: 0 }, 10, new Map())
    expect(result.selectable).toBe(false)
    expect(result.reason).toContain('停用')
  })

  it('跨分区资产禁选', () => {
    const result = holdEligibility({ id: 4, areaId: 20, status: 1 }, 10, new Map())
    expect(result.selectable).toBe(false)
    expect(result.reason).toContain('不属于所选分区')
  })

  it('追加占住时当前批次已有明细（含已释放）的资产禁选', () => {
    const result = holdEligibility(
      { id: 5, areaId: 10, status: 1 },
      10,
      new Map(),
      new Set([5])
    )
    expect(result.selectable).toBe(false)
    expect(result.reason).toContain('同一批次不能重复占住')

    // 不在已有明细中的资产仍可勾选
    expect(holdEligibility({ id: 6, areaId: 10, status: 1 }, 10, new Map(), new Set([5])).selectable).toBe(true)
  })

  it('状态文案映射', () => {
    expect(itemStatusText('HOLDING')).toBe('在占')
    expect(itemStatusText('TIMEOUT')).toBe('超时未到')
    expect(itemStatusText('RELEASED')).toBe('已释放')
    expect(batchStatusText('OPEN')).toBe('进行中')
    expect(batchStatusText('ENDED')).toBe('已结束')
  })
})
