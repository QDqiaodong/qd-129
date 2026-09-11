import { ref, computed, nextTick } from 'vue'

/**
 * 批量调区跨筛选勾选状态：
 * - 以资产 ID 为唯一键，切换分区/标签筛选时勾选不丢失
 * - 同一张桌椅重复勾选只保留一份
 * - rowCache 只保留最新版本的行数据，供预览/提交使用
 */
export function useDeskSelection(tableRef) {
  const selectedIds = ref(new Set())
  const rowCache = ref(new Map())

  const selectedRows = computed(() =>
    [...selectedIds.value].map(id => rowCache.value.get(id)).filter(Boolean)
  )

  const mergeRows = rows => {
    const list = rows || []
    list.forEach(row => {
      if (row && row.id != null) {
        rowCache.value.set(row.id, row)
      }
    })
  }

  const isSelected = row => !!row && selectedIds.value.has(row.id)

  const toggleRow = (row, selected) => {
    if (!row || row.id == null) return
    const next = new Set(selectedIds.value)
    if (selected) {
      next.add(row.id)
    } else {
      next.delete(row.id)
    }
    selectedIds.value = next
    rowCache.value.set(row.id, row)
  }

  const toggleRows = (rows, selected) => {
    const list = rows || []
    const next = new Set(selectedIds.value)
    list.forEach(row => {
      if (!row || row.id == null) return
      if (selected) {
        next.add(row.id)
      } else {
        next.delete(row.id)
      }
      rowCache.value.set(row.id, row)
    })
    selectedIds.value = next
  }

  /** 当前页全选/取消全选（el-table select-all 事件，selection 只含当前页数据） */
  const handleSelectAll = selection => {
    const selecting = (selection || []).length > 0
    toggleRows(selection, selecting)
  }

  /** 单行勾选/取消（el-table select 事件） */
  const handleSelect = (selection, row) => {
    toggleRow(row, selection.some(item => item.id === row.id))
  }

  /** 数据重新加载后，把跨筛选保留的勾选状态同步回当前页 */
  const syncTableSelection = async () => {
    await nextTick()
    const table = tableRef && tableRef.value
    if (!table) return
    const data = table.data || []
    data.forEach(row => {
      table.toggleRowSelection(row, isSelected(row))
    })
  }

  /** 清空勾选：一次清除全部筛选条件下累积的选择 */
  const clearAllSelection = () => {
    selectedIds.value = new Set()
    rowCache.value = new Map()
    tableRef && tableRef.value && tableRef.value.clearSelection()
  }

  return {
    selectedIds,
    selectedRows,
    mergeRows,
    isSelected,
    toggleRow,
    handleSelect,
    handleSelectAll,
    syncTableSelection,
    clearAllSelection
  }
}
