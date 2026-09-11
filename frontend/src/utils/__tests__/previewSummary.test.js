import { describe, it, expect } from 'vitest'
import {
  summarizeInvalidItems,
  buildInvalidAlertTitle,
  reasonText
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
    expect(reasonText('ALREADY_IN_TARGET')).toBe('已在目标分区')
    expect(reasonText('UNKNOWN')).toBe('资产不可迁移')
  })
})
