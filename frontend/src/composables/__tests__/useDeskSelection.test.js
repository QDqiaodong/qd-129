import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useDeskSelection } from '../useDeskSelection'

/**
 * 构造一个最小的 el-table 引用替身：
 * - data 表示当前筛选结果页
 * - toggleRowSelection 记录回放勾选状态的调用
 * - clearSelection 模拟表格清空
 */
function createTableRef(initialData = []) {
  const dataRef = ref(initialData)
  const toggled = ref([])
  const tableRef = ref({
    get data() {
      return dataRef.value
    },
    toggleRowSelection: vi.fn((row, selected) => {
      toggled.value.push({ id: row.id, selected })
    }),
    clearSelection: vi.fn()
  })
  return { tableRef, dataRef, toggled }
}

describe('useDeskSelection 跨筛选勾选', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('切换筛选条件后已勾选资产保留，且切回原页时勾选状态被回放', async () => {
    const { tableRef, dataRef } = createTableRef([
      { id: 1, assetCode: 'DC001' },
      { id: 2, assetCode: 'DC002' }
    ])
    const selection = useDeskSelection(tableRef)

    // 第一页勾选两张
    selection.handleSelect([{ id: 1 }], { id: 1 })
    selection.handleSelect([{ id: 1 }, { id: 2 }], { id: 2 })
    expect(selection.selectedRows.value).toHaveLength(2)

    // 切换到分区B的筛选结果（模拟 loadDeskChairs）
    dataRef.value = [{ id: 3, assetCode: 'DC003' }]
    await selection.syncTableSelection()
    // 跨筛选勾选不丢失
    expect(selection.selectedRows.value.map(r => r.id)).toEqual([1, 2])
    // 当前页没有已选项，不会对 id=3 回放勾选
    const callsAfterSwitch = tableRef.value.toggleRowSelection.mock.calls
    expect(callsAfterSwitch).toEqual([[{ id: 3, assetCode: 'DC003' }, false]])

    // 在分区B再勾选一张
    selection.handleSelect([{ id: 3 }], { id: 3 })
    expect(selection.selectedRows.value.map(r => r.id)).toEqual([1, 2, 3])

    // 切回分区A：两张都应回显勾选
    dataRef.value = [
      { id: 1, assetCode: 'DC001' },
      { id: 2, assetCode: 'DC002' }
    ]
    await selection.syncTableSelection()
    const replayCalls = tableRef.value.toggleRowSelection.mock.calls.slice(-2)
    expect(replayCalls).toEqual([
      [{ id: 1, assetCode: 'DC001' }, true],
      [{ id: 2, assetCode: 'DC002' }, true]
    ])
    // 界面提示数量与唯一资产一致
    expect(selection.selectedRows.value).toHaveLength(3)
  })

  it('同一张桌椅在不同筛选结果中重复勾选只保留一份（全选/单选混合）', () => {
    const { tableRef } = createTableRef([])
    const selection = useDeskSelection(tableRef)

    // 第一次筛选结果全选
    const page1 = [
      { id: 1, assetCode: 'DC001' },
      { id: 2, assetCode: 'DC002' }
    ]
    selection.handleSelectAll(page1)
    expect(selection.selectedRows.value).toHaveLength(2)

    // 第二次筛选结果与第一次有交集（id=2），再次全选
    const page2 = [
      { id: 2, assetCode: 'DC002', areaName: '第二阅览区' },
      { id: 3, assetCode: 'DC003' }
    ]
    selection.handleSelectAll(page2)
    expect(selection.selectedRows.value.map(r => r.id)).toEqual([1, 2, 3])

    // 单独再点一次 id=2 的勾选框（重复 add），数量不变
    selection.handleSelect(
      [{ id: 2 }, { id: 3 }],
      { id: 2 }
    )
    expect(selection.selectedRows.value).toHaveLength(3)
    expect(new Set(selection.selectedRows.value.map(r => r.id)).size).toBe(3)

    // 提交时按唯一资产 ID 去重，数量与界面“已勾选”提示一致
    const payloadIds = [...new Set(selection.selectedRows.value.map(r => r.id))]
    expect(payloadIds).toHaveLength(3)
  })

  it('取消勾选只影响对应资产，其他筛选条件下的勾选保留', () => {
    const { tableRef } = createTableRef([])
    const selection = useDeskSelection(tableRef)

    selection.handleSelectAll([{ id: 1 }, { id: 2 }])
    selection.handleSelectAll([{ id: 3 }])
    expect(selection.selectedRows.value).toHaveLength(3)

    // 在当前页取消 id=3
    selection.handleSelect([], { id: 3 })
    expect(selection.selectedRows.value.map(r => r.id)).toEqual([1, 2])
  })

  it('清空勾选一次清除全部筛选条件下累积的选择', () => {
    const { tableRef } = createTableRef([{ id: 3 }])
    const selection = useDeskSelection(tableRef)

    selection.handleSelectAll([{ id: 1 }, { id: 2 }])
    selection.handleSelectAll([{ id: 3 }])
    expect(selection.selectedRows.value).toHaveLength(3)

    selection.clearAllSelection()

    expect(selection.selectedRows.value).toHaveLength(0)
    expect(selection.selectedIds.value.size).toBe(0)
    expect(tableRef.value.clearSelection).toHaveBeenCalledTimes(1)

    // 清空后重新加载筛选结果不会再回放出勾选
    tableRef.value.toggleRowSelection.mockClear()
    return selection.syncTableSelection().then(() => {
      expect(tableRef.value.toggleRowSelection).toHaveBeenCalledTimes(1)
      expect(tableRef.value.toggleRowSelection).toHaveBeenCalledWith({ id: 3 }, false)
    })
  })

  it('行数据更新后缓存最新版本（如分区已被调整），提交使用最新行', () => {
    const { tableRef } = createTableRef([])
    const selection = useDeskSelection(tableRef)

    selection.handleSelect([{ id: 1, assetCode: 'DC001', areaId: 10 }],
      { id: 1, assetCode: 'DC001', areaId: 10 })
    // 模拟筛选重载拿到新行（归属已变）
    selection.mergeRows([{ id: 1, assetCode: 'DC001', areaId: 20 }, { id: 2, areaId: 20 }])
    const row1 = selection.selectedRows.value.find(r => r.id === 1)
    expect(row1.areaId).toBe(20)
  })
})
