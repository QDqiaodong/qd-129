import { describe, it, expect } from 'vitest'
import { parseStocktakeText } from '../parseStocktakeImport'

const areas = [
  { id: 1, areaCode: 'A001', areaName: '第一阅览区' },
  { id: 2, areaCode: 'A002', areaName: '第二阅览区' }
]

const tags = [
  { id: 10, tagCode: 'TAG001', tagName: '自习专用' },
  { id: 11, tagCode: 'TAG004', tagName: '双人桌' }
]

describe('parseStocktakeText', () => {
  it('parses full rows with header and resolves area/status/tags', () => {
    const text = [
      '资产编号,实盘分区,启用状态,标签',
      'DC001,第一阅览区,启用,自习专用、双人桌',
      'DC002,A002,停用,无标签'
    ].join('\n')

    const { lines, errors } = parseStocktakeText(text, { areas, tags })
    expect(errors).toEqual([])
    expect(lines).toHaveLength(2)
    expect(lines[0]).toEqual({
      assetCode: 'DC001',
      actualAreaId: 1,
      actualStatus: 1,
      actualTagIds: [10, 11]
    })
    expect(lines[1].actualAreaId).toBe(2)
    expect(lines[1].actualStatus).toBe(0)
    // “无标签”表示已核对为空标签集合
    expect(lines[1].actualTagIds).toEqual([])
  })

  it('supports tab separated and 1/0 status values', () => {
    const text = 'DC001\t第一阅览区\t1\t自习专用'
    const { lines, errors } = parseStocktakeText(text, { areas, tags })
    expect(errors).toEqual([])
    expect(lines[0].actualStatus).toBe(1)
    expect(lines[0].actualTagIds).toEqual([10])
  })

  it('leaves optional fields null when columns are empty (not checked)', () => {
    const { lines, errors } = parseStocktakeText('DC001,,,', { areas, tags })
    expect(errors).toEqual([])
    expect(lines[0]).toEqual({
      assetCode: 'DC001',
      actualAreaId: null,
      actualStatus: null,
      actualTagIds: null
    })
  })

  it('trims asset codes and detects duplicates', () => {
    const text = 'DC001,,,\n DC001 ,,,'
    const { lines, errors } = parseStocktakeText(text, { areas, tags })
    expect(lines).toHaveLength(1)
    expect(errors.some(e => e.includes('DC001'))).toBe(true)
  })

  it('collects row-level errors for missing code, unknown area, bad status and unknown tag', () => {
    const text = [
      ',,启用,',
      'DC002,不存在分区,异常,未知标签'
    ].join('\n')
    const { lines, errors } = parseStocktakeText(text, { areas, tags })
    expect(lines).toHaveLength(1)
    expect(errors.length).toBe(4)
    expect(errors.some(e => e.includes('缺少资产编号'))).toBe(true)
    expect(errors.some(e => e.includes('分区不存在'))).toBe(true)
    expect(errors.some(e => e.includes('启用状态无法识别'))).toBe(true)
    expect(errors.some(e => e.includes('标签不存在'))).toBe(true)
  })

  it('rejects empty text', () => {
    expect(parseStocktakeText('', { areas, tags }).errors).toEqual(['导入内容为空'])
    expect(parseStocktakeText('   \n  \n', { areas, tags }).errors).toEqual(['导入内容为空'])
  })
})
