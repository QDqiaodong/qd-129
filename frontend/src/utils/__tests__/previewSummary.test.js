import { describe, it, expect } from 'vitest'
import {
  summarizeInvalidItems,
  buildInvalidAlertTitle,
  reasonText,
  checkHoldConsistency,
  REASON_SEAT_HOLDING
} from '../previewSummary'

describe('previewSummary 失效资产逐项汇总', () => {
  const item = (overrides = {}) => ({
    deskChairId: 1,
    valid: true,
    status: 'READY',
    ...overrides
  })

  it('按原因码分别统计已删除、已停用、已在目标分区的数量', () => {
    const summary = summarizeInvalidItems([
      item({ deskChairId: 1, valid: false, reasonCode: 'NOT_FOUND', errorMessage: '资产不存在或已删除' }),
      item({ deskChairId: 2, valid: false, reasonCode: 'NOT_FOUND' }),
      item({ deskChairId: 3, valid: false, reasonCode: 'DISABLED', errorMessage: '资产已停用，无法调区' }),
      item({ deskChairId: 4, valid: false, reasonCode: 'ALREADY_IN_TARGET', errorMessage: '已在目标分区，无需迁移' }),
      item({ deskChairId: 5, valid: true })
    ])

    expect(summary.invalidCount).toBe(4)
    expect(summary.notFoundCount).toBe(2)
    expect(summary.disabledCount).toBe(1)
    expect(summary.seatHoldingCount).toBe(0)
    expect(summary.alreadyInTargetCount).toBe(1)
  })

  it('没有失效资产时各项计数为 0', () => {
    const summary = summarizeInvalidItems([item(), item()])
    expect(summary.invalidCount).toBe(0)
    expect(buildInvalidAlertTitle(summary)).toBe(
      '有 0 项不可迁移，整批不可提交，请调整勾选'
    )
  })

  it('提示标题只列出实际出现的失效类型，且总数与界面提交数量口径一致', () => {
    const mixed = summarizeInvalidItems([
      item({ valid: false, reasonCode: 'NOT_FOUND' }),
      item({ valid: false, reasonCode: 'DISABLED' }),
      item({ valid: false, reasonCode: 'ALREADY_IN_TARGET' }),
      item({ valid: false, reasonCode: 'ALREADY_IN_TARGET' })
    ])
    expect(buildInvalidAlertTitle(mixed)).toBe(
      '有 4 项不可迁移（资产已删除 1 项，资产已停用 1 项，已在目标分区 2 项），整批不可提交，请调整勾选'
    )

    const onlyDisabled = summarizeInvalidItems([
      item({ valid: false, reasonCode: 'DISABLED' })
    ])
    expect(buildInvalidAlertTitle(onlyDisabled)).toBe(
      '有 1 项不可迁移（资产已停用 1 项），整批不可提交，请调整勾选'
    )
  })

  it('进行中占座按批次号归并并写入提示，便于值班员对上是哪一批占座', () => {
    const summary = summarizeInvalidItems([
      item({ deskChairId: 1, valid: false, reasonCode: REASON_SEAT_HOLDING, seatHoldBatchNo: 'ZZ2026091201', assetCode: 'DC001' }),
      item({ deskChairId: 2, valid: false, reasonCode: REASON_SEAT_HOLDING, seatHoldBatchNo: 'ZZ2026091201', assetCode: 'DC002' }),
      item({ deskChairId: 3, valid: false, reasonCode: REASON_SEAT_HOLDING, seatHoldBatchNo: 'ZZ2026091202', assetCode: 'DC003' }),
      item({ deskChairId: 4, valid: false, reasonCode: 'DISABLED', assetCode: 'DC004' })
    ])

    expect(summary.seatHoldingCount).toBe(3)
    expect(summary.seatHoldingByBatch).toEqual({
      ZZ2026091201: 2,
      ZZ2026091202: 1
    })
    const title = buildInvalidAlertTitle(summary)
    expect(title).toContain('进行中占座批次占住 3 项')
    expect(title).toContain('批次 ZZ2026091201 2 项')
    expect(title).toContain('批次 ZZ2026091202 1 项')
    expect(title).toContain('请先到对应占座批次释放或调整勾选')
  })

  it('占座项缺少批次号时归入未知批次，不静默丢失', () => {
    const summary = summarizeInvalidItems([
      item({ valid: false, reasonCode: REASON_SEAT_HOLDING })
    ])
    expect(summary.seatHoldingByBatch).toEqual({ 未知批次: 1 })
    expect(buildInvalidAlertTitle(summary)).toContain('未知批次 1 项')
  })

  it('兼容缺失 reasonCode 的旧数据，只计入总数不分类', () => {
    const summary = summarizeInvalidItems([
      item({ valid: false }),
      item({ valid: false, errorMessage: '其他异常' })
    ])
    expect(summary.invalidCount).toBe(2)
    expect(summary.notFoundCount).toBe(0)
    expect(summary.disabledCount).toBe(0)
    expect(buildInvalidAlertTitle(summary)).toBe(
      '有 2 项不可迁移，整批不可提交，请调整勾选'
    )
  })

  it('reasonText 提供原因码中文文案', () => {
    expect(reasonText('NOT_FOUND')).toBe('资产已删除')
    expect(reasonText('DISABLED')).toBe('资产已停用')
    expect(reasonText('SEAT_HOLDING')).toBe('进行中占座')
    expect(reasonText('ALREADY_IN_TARGET')).toBe('已在目标分区')
    expect(reasonText('UNKNOWN')).toBe('资产不可迁移')
  })
})

describe('checkHoldConsistency 预览与占座列表对账', () => {
  const item = (overrides = {}) => ({
    deskChairId: 1,
    assetCode: 'DC001',
    valid: true,
    status: 'READY',
    ...overrides
  })

  const holdMap = entries => new Map(entries)

  it('预览占座原因与占座列表完全一致时无对账差异', () => {
    const items = [
      item({ deskChairId: 1, valid: false, reasonCode: REASON_SEAT_HOLDING, seatHoldBatchNo: 'ZZ1' }),
      item({ deskChairId: 2, assetCode: 'DC002' })
    ]
    const result = checkHoldConsistency(items, holdMap([
      [1, { batchNo: 'ZZ1', itemStatus: 'HOLDING' }]
    ]))
    expect(result.consistent).toBe(true)
    expect(result.mismatches).toHaveLength(0)
  })

  it('占座列表已有在占但预览未标记时给出 LIST_ONLY 明确提示', () => {
    const items = [
      item({ deskChairId: 1 }),
      item({ deskChairId: 2, assetCode: 'DC002', valid: false, reasonCode: 'DISABLED', errorMessage: '资产已停用，无法调区' })
    ]
    const result = checkHoldConsistency(items, holdMap([
      [2, { batchNo: 'ZZ9', itemStatus: 'HOLDING' }]
    ]))
    expect(result.consistent).toBe(false)
    expect(result.mismatches).toHaveLength(1)
    expect(result.mismatches[0].type).toBe('LIST_ONLY')
    expect(result.mismatches[0].deskChairId).toBe(2)
    expect(result.mismatches[0].message).toContain('ZZ9')
    expect(result.mismatches[0].message).toContain('重新预览')
  })

  it('预览标记的批次号与列表不一致时给出 STALE_BATCH 提示并写出两个批次号', () => {
    const items = [
      item({ valid: false, reasonCode: REASON_SEAT_HOLDING, seatHoldBatchNo: 'ZZ1' })
    ]
    const result = checkHoldConsistency(items, holdMap([
      [1, { batchNo: 'ZZ2', itemStatus: 'TIMEOUT' }]
    ]))
    expect(result.consistent).toBe(false)
    expect(result.mismatches[0].type).toBe('STALE_BATCH')
    expect(result.mismatches[0].message).toContain('ZZ2')
    expect(result.mismatches[0].message).toContain('ZZ1')
  })

  it('预览标记占座但列表已无记录（已释放）时给出 PREVIEW_ONLY 提示', () => {
    const items = [
      item({ valid: false, reasonCode: REASON_SEAT_HOLDING, seatHoldBatchNo: 'ZZ1' })
    ]
    const result = checkHoldConsistency(items, holdMap([]))
    expect(result.consistent).toBe(false)
    expect(result.mismatches[0].type).toBe('PREVIEW_ONLY')
    expect(result.mismatches[0].message).toContain('已无该在占记录')
  })

  it('占座列表缺失或非 Map 时按空列表处理', () => {
    const items = [item()]
    expect(checkHoldConsistency(items, null).consistent).toBe(true)
    expect(checkHoldConsistency(items, undefined).consistent).toBe(true)
  })
})
