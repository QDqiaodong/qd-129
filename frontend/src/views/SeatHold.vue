<template>
  <div class="page-container">
    <div class="page-header">
      <h2>高峰阅览占座批次</h2>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">开一批占座</el-button>
    </div>

    <!-- 批次列表筛选 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="阅览分区">
          <el-select v-model="filters.areaId" placeholder="全部分区" clearable style="width: 220px" @change="loadBatches">
            <el-option
              v-for="area in readingAreas"
              :key="area.id"
              :label="areaFilterLabel(area)"
              :value="area.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="批次状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 160px" @change="loadBatches">
            <el-option label="进行中" value="OPEN" />
            <el-option label="已结束" value="ENDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilter">重置</el-button>
          <el-button @click="loadBatches">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="batch-card">
      <el-table :data="batches" border>
        <el-table-column prop="batchNo" label="批次号" width="190" />
        <el-table-column label="占座分区" min-width="160">
          <template #default="scope">
            {{ scope.row.areaName }}（{{ scope.row.areaCode }}）
            <el-tag
              v-if="batchAreaClosed(scope.row.areaId)"
              type="danger"
              size="small"
              effect="plain"
              class="closed-inline-tag"
            >闭馆中</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="timeSlot" label="高峰时段" min-width="170" show-overflow-tooltip />
        <el-table-column label="在占/超时/已释放" width="150">
          <template #default="scope">
            <span class="count-group">
              <span class="held-num">{{ scope.row.heldCount }}</span> /
              <span :class="{ 'timeout-num': scope.row.timeoutCount > 0 }">{{ scope.row.timeoutCount }}</span> /
              {{ scope.row.releasedCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="总占住" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'OPEN' ? 'warning' : 'info'" size="small">
              {{ batchStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="值班员" width="90" />
        <el-table-column label="创建时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button size="small" link type="primary" @click="openDetail(scope.row.id)">
              {{ scope.row.status === 'OPEN' ? '占座处理' : '查看详情' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="batches.length === 0" description="暂无占座批次" />
    </el-card>

    <!-- 开批：选分区 + 时段 -->
    <el-dialog v-model="createVisible" title="开一批高峰占座" width="560px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="占座分区" prop="areaId">
          <el-select v-model="createForm.areaId" placeholder="请选择阅览分区" style="width: 100%" @change="onCreateAreaChange">
            <el-option
              v-for="area in areaOptions"
              :key="area.id"
              :label="`${area.areaName}（${area.areaCode}）`"
              :value="area.id"
              :disabled="isAreaClosed(area)"
            >
              <span>{{ area.areaName }}（{{ area.areaCode }}）</span>
              <span v-if="isAreaClosed(area)" class="closed-option-tag">今日闭馆至 {{ closedUntilShort(area.closedUntil) }}</span>
            </el-option>
          </el-select>
          <el-alert
            v-if="selectedCreateAreaClosed"
            type="error"
            :closable="false"
            show-icon
            class="closed-alert"
            :title="`该阅览区今日闭馆，闭馆至 ${formatClosedUntil(selectedCreateAreaClosed.closedUntil)}，结束时刻后才能开新批次占座。`"
          />
        </el-form-item>
        <el-form-item label="高峰时段" prop="timeSlot">
          <el-select
            v-model="createForm.timeSlot"
            placeholder="请选择高峰时段"
            style="width: 100%"
            allow-create
            filterable
            default-first-option
          >
            <el-option v-for="slot in PEAK_TIME_SLOTS" :key="slot.value" :label="slot.label" :value="slot.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="值班员" prop="operator">
          <el-input v-model="createForm.operator" maxlength="100" placeholder="请填写值班员" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!canOpenPicker" @click="openPicker()">
          下一步：从桌椅档案勾选资产
        </el-button>
      </template>
    </el-dialog>

    <!-- 从桌椅档案勾选资产（开批 / 追加占住共用） -->
    <el-dialog
      v-model="pickerVisible"
      :title="pickerMode === 'create' ? '勾选占住资产' : `追加占住 · ${editingBatch?.batchNo || ''}`"
      width="1000px"
      append-to-body
      @closed="handlePickerClosed"
    >
      <el-alert
        v-if="blockedCount > 0"
        :title="`当前筛选结果有 ${blockedCount} 件不可占（停用/报修/已被其他进行中批次占住/有待领取遗失物品），勾选框已禁用，悬停或查看“不可占原因”列。`"
        type="warning"
        :closable="false"
        show-icon
        class="picker-alert"
      />
      <el-form :inline="true" :model="pickerFilters">
        <el-form-item label="资产编号">
          <el-input v-model="pickerFilters.keyword" placeholder="如 DC001" clearable style="width: 160px" @keyup.enter="loadPickerDesks" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPickerDesks">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="pickerTableRef"
        :data="pickerRows"
        border
        row-key="id"
        height="420"
        @select="handlePickerSelect"
        @select-all="handlePickerSelectAll"
      >
        <el-table-column type="selection" width="50" :selectable="canSelectRow" reserve-selection />
        <el-table-column prop="assetCode" label="资产编号" width="110" />
        <el-table-column prop="capacity" label="容纳人数" width="90" />
        <el-table-column prop="areaName" label="所属分区" width="140" />
        <el-table-column label="尺寸" width="140">
          <template #default="scope">{{ scope.row.dimensions || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="Number(scope.row.status) === 1 ? 'success' : 'info'" size="small">
              {{ Number(scope.row.status) === 1 ? '可用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="不可占原因" min-width="220">
          <template #default="scope">
            <span v-if="!canSelectRow(scope.row)" class="block-reason">{{ blockReason(scope.row) }}</span>
            <span v-else class="can-hold">可占住</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="picker-footer">
        <div class="selection-info">
          已勾选 <span class="selection-count">{{ pickerSelectedIds.size }}</span> 件资产
          <span class="selection-tip">（跨查询条件勾选会保留，仅统计可占资产）</span>
        </div>
        <div>
          <el-button @click="clearPickerSelection">清空勾选</el-button>
          <el-button @click="pickerVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="pickerSubmitting"
            :disabled="pickerSelectedIds.size === 0"
            @click="submitPicker"
          >
            {{ pickerMode === 'create'
              ? `开批并占住 ${pickerSelectedIds.size} 件`
              : `追加占住 ${pickerSelectedIds.size} 件` }}
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 批次详情 / 占座处理抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail ? `占座批次 ${detail.batchNo}` : '占座详情'"
      size="78%"
      destroy-on-close
    >
      <template v-if="detail">
        <el-descriptions :column="4" border size="small" class="detail-desc">
          <el-descriptions-item label="占座分区">
            {{ detail.areaName }}（{{ detail.areaCode }}）
          </el-descriptions-item>
          <el-descriptions-item label="高峰时段">{{ detail.timeSlot }}</el-descriptions-item>
          <el-descriptions-item label="批次状态">
            <el-tag :type="detail.status === 'OPEN' ? 'warning' : 'info'" size="small">
              {{ batchStatusText(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="值班员">{{ detail.operator }}</el-descriptions-item>
          <el-descriptions-item label="在占">{{ detail.heldCount }}</el-descriptions-item>
          <el-descriptions-item label="超时未到">{{ detail.timeoutCount }}</el-descriptions-item>
          <el-descriptions-item label="已释放">{{ detail.releasedCount }}</el-descriptions-item>
          <el-descriptions-item label="总占住">{{ detail.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.endedAt" label="结束时间" :span="2">
            {{ formatTime(detail.endedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="detail.endedAt ? 1 : 3">
            {{ detail.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.status === 'OPEN'"
          type="warning"
          :closable="false"
          show-icon
          title="批次进行中：勾选占住的桌椅已在档案中停用；当场释放会立即恢复可用，标记超时未到则继续停用直到整批结束。"
          class="lock-alert"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="批次已结束：仍占着/超时未到的资产保持停用，清场确认无人后可逐条“释放恢复”；已释放资产维持可用。"
          class="lock-alert"
        />

        <div v-if="detail.status === 'OPEN'" class="action-bar">
          <el-button type="primary" plain @click="openPicker('append')">追加占住资产</el-button>
          <el-button
            type="success"
            :loading="finishLoading"
            @click="handleFinish"
          >
            整批结束
          </el-button>
          <span class="action-tip">结束后在占/超时未到的资产继续停用，由清场逐条释放。</span>
        </div>

        <el-table :data="detail.items" border size="small" class="item-table">
          <el-table-column prop="assetCode" label="资产编号" width="110" />
          <el-table-column prop="areaName" label="所属分区" width="130" />
          <el-table-column label="明细状态" width="100">
            <template #default="scope">
              <el-tag :type="itemTagType(scope.row.itemStatus)" size="small">
                {{ itemStatusText(scope.row.itemStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="桌椅实时状态" width="110">
            <template #default="scope">
              <el-tag :type="Number(scope.row.deskStatus) === 1 ? 'success' : 'danger'" size="small">
                {{ Number(scope.row.deskStatus) === 1 ? '可用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="占住前状态" width="90">
            <template #default="scope">{{ Number(scope.row.previousDeskStatus) === 1 ? '可用' : '停用' }}</template>
          </el-table-column>
          <el-table-column label="释放信息" min-width="180">
            <template #default="scope">
              <div v-if="scope.row.releasedAt">
                {{ scope.row.releasedBy }} · {{ formatTime(scope.row.releasedAt) }}
              </div>
              <div v-else-if="scope.row.timeoutAt" class="timeout-meta">
                超时标记：{{ scope.row.timeoutBy }} · {{ formatTime(scope.row.timeoutAt) }}
              </div>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="scope">
              <template v-if="detail.status === 'OPEN'">
                <template v-if="scope.row.itemStatus === 'HOLDING'">
                  <el-button link type="primary" size="small" @click="handleRelease(scope.row)">当场释放</el-button>
                  <el-button link type="warning" size="small" @click="handleMarkTimeout(scope.row)">改超时未到</el-button>
                </template>
                <el-button
                  v-else-if="scope.row.itemStatus === 'TIMEOUT'"
                  link
                  type="info"
                  size="small"
                  @click="handleRevertTimeout(scope.row)"
                >撤回到在占</el-button>
                <span v-else class="readonly-text">已闭环</span>
              </template>
              <template v-else>
                <el-button
                  v-if="scope.row.itemStatus !== 'RELEASED'"
                  link
                  type="primary"
                  size="small"
                  @click="handleReleaseLegacy(scope.row)"
                >清场释放恢复</el-button>
                <span v-else class="readonly-text">已恢复可用</span>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { seatHoldApi, readingAreaApi, deskChairApi, lostItemApi } from '../api'
import { PEAK_TIME_SLOTS, indexActiveHolds, indexPendingLostItems, holdEligibility, itemStatusText, batchStatusText, mergeBatchIntoList } from '../utils/peakSlot'
import { isAreaClosed, formatClosedUntil, closedUntilShort, closedAreaLabel } from '../utils/areaClosure'

const batches = ref([])
const readingAreas = ref([])
const filters = reactive({ areaId: null, status: null })

// —— 开批 ——
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ areaId: null, timeSlot: null, operator: '', remark: '' })
const createRules = {
  areaId: [{ required: true, message: '请选择占座分区', trigger: 'change' }],
  timeSlot: [{ required: true, message: '请选择高峰时段', trigger: 'change' }],
  operator: [{ required: true, message: '请填写值班员', trigger: 'blur' }]
}

// —— 勾选抽屉 ——
const pickerVisible = ref(false)
const pickerMode = ref('create') // create / append
const pickerTableRef = ref()
const pickerFilters = reactive({ keyword: '' })
const pickerRows = ref([])
const pickerRowCache = new Map()
const pickerSelectedIds = ref(new Set())
const pickerSelectedRows = ref([])
const pickerActiveHolds = ref(new Map())
const pickerPendingLost = ref(new Map())
const pickerSubmitting = ref(false)
const editingBatch = ref(null)

// —— 详情 ——
const detailVisible = ref(false)
const detail = ref(null)
const finishLoading = ref(false)

const areaOptions = computed(() =>
  readingAreas.value.filter(area => area.status === 1 || area.status === undefined)
)

const selectedCreateArea = computed(() =>
  readingAreas.value.find(area => area.id === createForm.areaId) || null
)
const selectedCreateAreaClosed = computed(() =>
  selectedCreateArea.value && isAreaClosed(selectedCreateArea.value) ? selectedCreateArea.value : null
)
const canOpenPicker = computed(() => !!createForm.areaId && !selectedCreateAreaClosed.value)

// 批次列表/筛选里的分区闭馆角标；到期未刷新也能按当前时刻自动消失
const areaById = id => readingAreas.value.find(area => area.id === id)
const batchAreaClosed = areaId => isAreaClosed(areaById(areaId))
const areaFilterLabel = area =>
  isAreaClosed(area) ? `${area.areaName}（${closedAreaLabel(area)}）` : area.areaName

const blockedCount = computed(() => pickerRows.value.filter(row => !canSelectRow(row)).length)

const loadBatches = async () => {
  batches.value = await seatHoldApi.listBatches({ areaId: filters.areaId, status: filters.status })
}

const loadAreas = async () => {
  readingAreas.value = await readingAreaApi.getAll()
}

const resetFilter = () => {
  filters.areaId = null
  filters.status = null
  loadBatches()
}

const openCreateDialog = () => {
  createForm.areaId = null
  createForm.timeSlot = null
  createForm.operator = ''
  createForm.remark = ''
  createVisible.value = true
}

const onCreateAreaChange = () => {
  // 分区改变后重新进入勾选页时会重算在占提示
}

const canSelectRow = row => {
  const areaId = pickerMode.value === 'create' ? createForm.areaId : editingBatch.value?.areaId
  return holdEligibility(row, areaId, pickerActiveHolds.value, existingInBatchIds.value, pickerPendingLost.value).selectable
}

const blockReason = row => holdEligibility(
  row,
  pickerMode.value === 'create' ? createForm.areaId : editingBatch.value?.areaId,
  pickerActiveHolds.value,
  existingInBatchIds.value,
  pickerPendingLost.value
).reason

// 追加占住时，当前批次已有明细（含已释放）的资产禁选，一件资产一批只占一次
const existingInBatchIds = computed(() => {
  if (pickerMode.value !== 'append' || !editingBatch.value?.items) return new Set()
  return new Set(editingBatch.value.items.map(item => item.deskChairId))
})

const cacheRows = rows => {
  rows.forEach(row => {
    if (row && row.id != null) {
      pickerRowCache.set(row.id, row)
    }
  })
}

const syncPickerSelection = () => {
  const table = pickerTableRef.value
  if (!table) return
  pickerRows.value.forEach(row => {
    table.toggleRowSelection(row, pickerSelectedIds.value.has(row.id) && canSelectRow(row))
  })
}

const loadPickerDesks = async () => {
  const areaId = pickerMode.value === 'create' ? createForm.areaId : editingBatch.value?.areaId
  // 含停用资产：停用原因（报修/他批占住）需要在列表中明确展示
  let rows = await deskChairApi.getAll({ params: { includeDisabled: true } })
  if (areaId != null) {
    rows = rows.filter(row => row.areaId === areaId)
  }
  const keyword = pickerFilters.keyword.trim().toUpperCase()
  if (keyword) {
    rows = rows.filter(row => (row.assetCode || '').toUpperCase().includes(keyword))
  }
  cacheRows(rows)
  pickerRows.value = rows
  await syncPickerSelection()
}

const rebuildSelectedRows = () => {
  pickerSelectedRows.value = [...pickerSelectedIds.value]
    .map(id => pickerRowCache.get(id))
    .filter(Boolean)
}

const handlePickerSelect = (selection, row) => {
  if (!canSelectRow(row)) return
  const next = new Set(pickerSelectedIds.value)
  if (selection.some(item => item.id === row.id)) {
    next.add(row.id)
  } else {
    next.delete(row.id)
  }
  pickerSelectedIds.value = next
  rebuildSelectedRows()
}

const handlePickerSelectAll = selection => {
  const next = new Set(pickerSelectedIds.value)
  const selectableRows = pickerRows.value.filter(canSelectRow)
  const selecting = selection.length > 0
  selectableRows.forEach(row => {
    if (selecting) {
      next.add(row.id)
    } else {
      next.delete(row.id)
    }
  })
  pickerSelectedIds.value = next
  rebuildSelectedRows()
}

const clearPickerSelection = () => {
  pickerSelectedIds.value = new Set()
  pickerSelectedRows.value = []
  pickerTableRef.value && pickerTableRef.value.clearSelection()
}

const handlePickerClosed = () => {
  clearPickerSelection()
  pickerRows.value = []
  pickerFilters.keyword = ''
  pickerActiveHolds.value = new Map()
  pickerPendingLost.value = new Map()
}

const openPicker = async mode => {
  if (mode === 'append') {
    if (!detail.value || detail.value.status !== 'OPEN') return
    pickerMode.value = 'append'
    editingBatch.value = detail.value
  } else {
    try {
      await createFormRef.value.validate()
    } catch (e) {
      return
    }
    // 双保险：下拉已禁用闭馆分区，这里再拦一次并发挂牌/数据陈旧
    if (selectedCreateAreaClosed.value) {
      ElMessage.error(
        `该阅览区今日闭馆，闭馆至 ${formatClosedUntil(selectedCreateAreaClosed.value.closedUntil)}，结束时刻后再开批`
      )
      return
    }
    pickerMode.value = 'create'
    editingBatch.value = null
  }
  const areaId = pickerMode.value === 'create' ? createForm.areaId : editingBatch.value.areaId
  pickerVisible.value = true
  const [holds, pendingLost] = await Promise.all([
    seatHoldApi.listActiveHolds(areaId),
    lostItemApi.listPending(areaId),
    loadPickerDesks()
  ])
  pickerActiveHolds.value = indexActiveHolds(holds)
  pickerPendingLost.value = indexPendingLostItems(pendingLost)
  await syncPickerSelection()
}

const submitPicker = async () => {
  rebuildSelectedRows()
  const ids = pickerSelectedRows.value.map(row => row.id)
  if (ids.length === 0) {
    ElMessage.error('请先勾选资产')
    return
  }
  if (pickerMode.value === 'create') {
    try {
      await ElMessageBox.confirm(
        `本次开批将占住 ${ids.length} 件资产，占住后桌椅立即停用，是否继续？`,
        '开批占座',
        { type: 'warning', confirmButtonText: '开批并占住', cancelButtonText: '取消' }
      )
    } catch (e) {
      return
    }
    pickerSubmitting.value = true
    try {
      const batch = await seatHoldApi.createBatch({
        areaId: createForm.areaId,
        timeSlot: createForm.timeSlot,
        deskChairIds: ids,
        remark: createForm.remark,
        operator: createForm.operator
      })
      ElMessage.success(`占座批次 ${batch.batchNo} 已开批，${batch.heldCount} 件资产已占住停用`)
      pickerVisible.value = false
      createVisible.value = false
      await loadBatches()
      await openDetail(batch.id)
    } catch (e) {
      ElMessage.error(e.message || '开批失败')
    } finally {
      pickerSubmitting.value = false
    }
    return
  }

  pickerSubmitting.value = true
  try {
    const before = detail.value
    const updated = await seatHoldApi.hold(editingBatch.value.id, {
      deskChairIds: ids,
      operator: detail.value.operator
    })
    ElMessage.success(`已追加占住 ${updated.heldCount - before.heldCount} 件资产`)
    pickerVisible.value = false
    await syncAfterMutation(updated)
  } catch (e) {
    ElMessage.error(e.message || '追加占住失败')
  } finally {
    pickerSubmitting.value = false
  }
}

const openDetail = async id => {
  try {
    detail.value = await seatHoldApi.getBatch(id)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载批次详情失败')
  }
}

// 处置成功后：先用接口返回的最新批次同步抽屉与列表计数（立即生效，不依赖二次列表请求），
// 再带当前筛选条件后台刷新一次；filters 全程不动，进行中等筛选不会丢
const syncAfterMutation = updated => {
  detail.value = { ...detail.value, ...updated }
  batches.value = mergeBatchIntoList(batches.value, updated)
  return loadBatches()
}

const handleRelease = async item => {
  try {
    await ElMessageBox.confirm(
      `确认当场释放 ${item.assetCode}？释放后该桌椅立即恢复为占住前状态（${Number(item.previousDeskStatus) === 1 ? '可用' : '停用'}）。`,
      '当场释放',
      { type: 'warning', confirmButtonText: '释放', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    const updated = await seatHoldApi.release(detail.value.id, item.id, { operator: detail.value.operator })
    ElMessage.success(`${item.assetCode} 已释放`)
    await syncAfterMutation(updated)
  } catch (e) {
    ElMessage.error(e.message || '释放失败')
  }
}

const handleMarkTimeout = async item => {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认将 ${item.assetCode} 标记为“超时未到”？标记后桌椅继续停用，直到整批结束清场处置。`,
      '改超时未到',
      {
        confirmButtonText: '确认超时未到',
        cancelButtonText: '取消',
        inputPlaceholder: '可填值班员（默认批次值班员）',
        inputValue: detail.value.operator,
        inputValidator: v => (v && v.trim() ? true : '请填写值班员')
      }
    )
    const updated = await seatHoldApi.markTimeout(detail.value.id, item.id, { operator: value })
    ElMessage.success(`${item.assetCode} 已标记超时未到`)
    await syncAfterMutation(updated)
  } catch (e) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e.message || '标记失败')
  }
}

const handleRevertTimeout = async item => {
  try {
    const updated = await seatHoldApi.revertTimeout(detail.value.id, item.id, { operator: detail.value.operator })
    ElMessage.success(`${item.assetCode} 已撤回到在占`)
    await syncAfterMutation(updated)
  } catch (e) {
    ElMessage.error(e.message || '撤回失败')
  }
}

const handleFinish = async () => {
  const unresolved = detail.value.heldCount + detail.value.timeoutCount
  try {
    await ElMessageBox.confirm(
      `整批结束后，${unresolved} 件仍在占/超时未到的资产将保持停用（清场后逐条释放），已释放的 ${detail.value.releasedCount} 件维持可用。确认结束？`,
      '整批结束',
      { type: 'warning', confirmButtonText: '结束批次', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  finishLoading.value = true
  try {
    const updated = await seatHoldApi.finishBatch(detail.value.id, { operator: detail.value.operator })
    ElMessage.success('批次已结束，遗留停用资产可在详情中清场释放')
    detail.value = { ...detail.value, ...updated }
    // 结束后批次可能掉出“进行中”筛选：用带当前筛选的刷新重算列表（筛选条件仍保留，不重置）
    await loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '结束失败')
  } finally {
    finishLoading.value = false
  }
}

const handleReleaseLegacy = async item => {
  try {
    const { value } = await ElMessageBox.prompt(
      `清场确认：${item.assetCode} 已无人使用，释放后恢复为占住前状态（${Number(item.previousDeskStatus) === 1 ? '可用' : '停用'}）。请填写处置值班员。`,
      '清场释放恢复',
      {
        confirmButtonText: '释放恢复',
        cancelButtonText: '取消',
        inputPlaceholder: '处置值班员',
        inputValue: detail.value.operator,
        inputValidator: v => (v && v.trim() ? true : '请填写处置值班员')
      }
    )
    const updated = await seatHoldApi.releaseLegacy(detail.value.id, item.id, { operator: value })
    ElMessage.success(`${item.assetCode} 已释放恢复`)
    await syncAfterMutation(updated)
  } catch (e) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e.message || '释放失败')
  }
}

const itemTagType = status => ({
  HOLDING: 'danger',
  TIMEOUT: 'warning',
  RELEASED: 'success'
}[status] || 'info')

const formatTime = time => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadAreas()
  loadBatches()
})
</script>

<style scoped>
.page-container {
  max-width: 1500px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 600;
}

.filter-card,
.batch-card {
  margin-bottom: 20px;
}

.count-group {
  font-variant-numeric: tabular-nums;
}

.held-num {
  color: #f56c6c;
  font-weight: 600;
}

.timeout-num {
  color: #e6a23c;
  font-weight: 600;
}

.picker-alert {
  margin-bottom: 12px;
}

.closed-alert {
  margin-top: 8px;
}

.closed-option-tag {
  float: right;
  color: #f56c6c;
  font-size: 12px;
}

.closed-inline-tag {
  margin-left: 6px;
}

.block-reason {
  color: #f56c6c;
  font-size: 12px;
}

.can-hold {
  color: #67c23a;
  font-size: 12px;
}

.picker-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
}

.selection-count {
  font-weight: 600;
  color: #409eff;
}

.selection-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 6px;
}

.detail-desc {
  margin-bottom: 16px;
}

.lock-alert {
  margin-bottom: 16px;
}

.action-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

.action-tip {
  color: #909399;
  font-size: 12px;
}

.item-table {
  margin-bottom: 16px;
}

.timeout-meta {
  color: #e6a23c;
  font-size: 12px;
}

.readonly-text {
  color: #909399;
  font-size: 12px;
}
</style>
