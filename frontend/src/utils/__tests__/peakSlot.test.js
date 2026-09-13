import { describe, it, expect } from 'vitest'
import {
  PEAK_TIME_SLOTS,
  indexActiveHolds,
  indexPendingLostItems,
  holdEligibility,
  itemStatusText,
  batchStatusText,
  mergeBatchIntoList
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

  it('indexPendingLostItems 按资产 ID 索引待领取遗失物品', () => {
    const map = indexPendingLostItems([
      { deskChairId: 1, itemNo: 'YW001', itemName: '黑色双肩包' },
      { deskChairId: 2, itemNo: 'YW002', itemName: '水杯' },
      { deskChairId: 1, itemNo: 'YW009', itemName: '雨伞' },
      null
    ])
    expect(map.size).toBe(2)
    // 同一资产多条待领时保留最新一条（列表按时间倒序，先出现者优先）
    expect(map.get(1).itemNo).toBe('YW001')
    expect(map.get(2).itemName).toBe('水杯')
  })

  it('有待领取遗失物品的桌椅禁选并给出含单号的明确原因', () => {
    const lostMap = indexPendingLostItems([{ deskChairId: 7, itemNo: 'YW20260912001', itemName: '黑色双肩包' }])
    const result = holdEligibility({ id: 7, areaId: 10, status: 1 }, 10, new Map(), new Set(), lostMap)
    expect(result.selectable).toBe(false)
    expect(result.reason).toContain('YW20260912001')
    expect(result.reason).toContain('黑色双肩包')
    expect(result.reason).toContain('不能开高峰占座')

    // 无待领取遗失物品（已领取闭环）的同状态资产可正常勾选
    expect(
      holdEligibility({ id: 8, areaId: 10, status: 1 }, 10, new Map(), new Set(), lostMap).selectable
    ).toBe(true)
  })

  it('状态文案映射', () => {
    expect(itemStatusText('HOLDING')).toBe('在占')
    expect(itemStatusText('TIMEOUT')).toBe('超时未到')
    expect(itemStatusText('RELEASED')).toBe('已释放')
    expect(batchStatusText('OPEN')).toBe('进行中')
    expect(batchStatusText('ENDED')).toBe('已结束')
  })

  describe('mergeBatchIntoList 处置后立即同步批次列表计数', () => {
    const list = () => [
      { id: 1, batchNo: 'ZZ001', areaName: '第一阅览区', areaCode: 'A001', status: 'OPEN', heldCount: 2, timeoutCount: 0, releasedCount: 0, totalCount: 2 },
      { id: 2, batchNo: 'ZZ002', areaName: '第二阅览区', areaCode: 'A002', status: 'OPEN', heldCount: 1, timeoutCount: 0, releasedCount: 0, totalCount: 1 }
    ]

    it('释放后立即把对应行的在占/已释放计数改成最新值，其他行与顺序不变', () => {
      const updated = { id: 1, status: 'OPEN', heldCount: 1, timeoutCount: 0, releasedCount: 1, totalCount: 2 }
      const result = mergeBatchIntoList(list(), updated)
      expect(result[0].heldCount).toBe(1)
      expect(result[0].releasedCount).toBe(1)
      // 列表联表冗余字段不能被处置接口（不含分区名）覆盖掉
      expect(result[0].areaName).toBe('第一阅览区')
      expect(result[0].areaCode).toBe('A001')
      expect(result[0].batchNo).toBe('ZZ001')
      // 其他批次不受影响
      expect(result[1].id).toBe(2)
      expect(result[1].heldCount).toBe(1)
    })

    it('改超时/撤回同样同步超时与在占计数', () => {
      const timedOut = mergeBatchIntoList(list(), { id: 2, status: 'OPEN', heldCount: 0, timeoutCount: 1, releasedCount: 0, totalCount: 1 })
      expect(timedOut[1].heldCount).toBe(0)
      expect(timedOut[1].timeoutCount).toBe(1)
      const reverted = mergeBatchIntoList(timedOut, { id: 2, status: 'OPEN', heldCount: 1, timeoutCount: 0, releasedCount: 0, totalCount: 1 })
      expect(reverted[1].heldCount).toBe(1)
      expect(reverted[1].timeoutCount).toBe(0)
    })

    it('批次掉出当前筛选结果时不强行插入（进行中筛选不被破坏）', () => {
      const result = mergeBatchIntoList(list(), { id: 99, status: 'ENDED', heldCount: 0, timeoutCount: 0, releasedCount: 3, totalCount: 3 })
      expect(result).toHaveLength(2)
      expect(result.find(b => b.id === 99)).toBeUndefined()
    })

    it('不就地改写原数组（返回新数组、新行对象），其余行保持同引用以减少重渲染', () => {
      const source = list()
      const result = mergeBatchIntoList(source, { id: 1, heldCount: 1, timeoutCount: 0, releasedCount: 1, totalCount: 2 })
      expect(result).not.toBe(source)
      expect(result[0]).not.toBe(source[0])
      expect(result[1]).toBe(source[1])
    })

    it('入参为空/非法时安全返回空数组或原列表', () => {
      expect(mergeBatchIntoList(null, { id: 1 })).toEqual([])
      const source = list()
      expect(mergeBatchIntoList(source, null)).toBe(source)
      expect(mergeBatchIntoList(source, {})).toBe(source)
    })
  })
})
