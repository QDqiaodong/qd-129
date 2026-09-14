import { describe, it, expect } from 'vitest'
import { hasInspectionProblem, validateInspectionEntry, canCompleteBatch, resultText } from '../nightInspection'

describe('夜间巡检逐件登记校验', () => {
  it('没写灯或插座结果不能提交该件', () => {
    expect(validateInspectionEntry({})).toContain('灯')
    expect(validateInspectionEntry({ socketResult: 'NORMAL', deskSurfaceResult: 'NORMAL' })).toContain('灯')
    expect(validateInspectionEntry({ lightResult: 'NORMAL', deskSurfaceResult: 'NORMAL' })).toContain('插座')
    expect(validateInspectionEntry({ lightResult: 'NORMAL', socketResult: 'NORMAL' })).toContain('桌面')
  })

  it('三项都正常时无需处理意见即可提交', () => {
    expect(validateInspectionEntry({
      lightResult: 'NORMAL', socketResult: 'NORMAL', deskSurfaceResult: 'NORMAL'
    })).toBeNull()
  })

  it('任一项异常即有问题，必须写处理意见', () => {
    expect(hasInspectionProblem({ lightResult: 'ABNORMAL' })).toBe(true)
    expect(hasInspectionProblem({ socketResult: 'ABNORMAL' })).toBe(true)
    expect(hasInspectionProblem({ deskSurfaceResult: 'ABNORMAL' })).toBe(true)
    expect(hasInspectionProblem({
      lightResult: 'NORMAL', socketResult: 'NORMAL', deskSurfaceResult: 'NORMAL'
    })).toBe(false)

    const problemEntry = { lightResult: 'ABNORMAL', socketResult: 'NORMAL', deskSurfaceResult: 'NORMAL' }
    expect(validateInspectionEntry(problemEntry)).toContain('处理意见')
    expect(validateInspectionEntry({ ...problemEntry, handleOpinion: '  ' })).toContain('处理意见')
    expect(validateInspectionEntry({ ...problemEntry, handleOpinion: '已报修' })).toBeNull()
  })

  it('批次没巡完不能结束', () => {
    expect(canCompleteBatch(null)).toBe(false)
    expect(canCompleteBatch({ totalCount: 0, checkedCount: 0 })).toBe(false)
    expect(canCompleteBatch({ totalCount: 3, checkedCount: 2 })).toBe(false)
    expect(canCompleteBatch({ totalCount: 3, checkedCount: 3 })).toBe(true)
  })

  it('结果文案按部位区分异常描述', () => {
    expect(resultText('light', 'ABNORMAL')).toBe('不亮')
    expect(resultText('socket', 'ABNORMAL')).toBe('失灵')
    expect(resultText('deskSurface', 'ABNORMAL')).toBe('涂鸦/破损')
    expect(resultText('light', 'NORMAL')).toBe('正常')
    expect(resultText('light', null)).toBe('未登记')
  })
})
