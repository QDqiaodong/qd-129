<template>
  <div class="page-container">
    <div class="page-header">
      <h2>桌椅批量调区工作台</h2>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="阅览分区">
          <el-select v-model="filters.areaId" placeholder="全部分区" clearable style="width: 200px">
            <el-option v-for="area in readingAreas" :key="area.id" :label="area.areaName" :value="area.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签筛选">
          <el-select v-model="filters.tagIds" placeholder="全部标签" multiple collapse-tags collapse-tags-tooltip style="width: 260px">
            <el-option v-for="tag in tags" :key="tag.id" :label="tag.tagName" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-toolbar">
        <div class="selection-info">
          已勾选 <span class="selection-count">{{ selectedRows.length }}</span> 张桌椅
          <span class="selection-tip">（跨筛选条件勾选会保留）</span>
        </div>
        <div>
          <el-button @click="clearSelection">清空勾选</el-button>
          <el-button type="primary" :disabled="selectedRows.length === 0" @click="openTransferDialog">
            批量调区
          </el-button>
        </div>
      </div>

      <el-table
        ref="deskTableRef"
        :data="deskChairs"
        border
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" reserve-selection />
        <el-table-column prop="assetCode" label="资产编号" width="110" />
        <el-table-column prop="capacity" label="容纳人数" width="90" />
        <el-table-column prop="areaName" label="当前分区" width="140" />
        <el-table-column prop="dimensions" label="尺寸" width="140" />
        <el-table-column label="标签" min-width="180">
          <template #default="scope">
            <el-tag
              v-for="tag in scope.row.tags"
              :key="tag.id"
              :style="{ background: tag.tagColor, borderColor: tag.tagColor }"
              size="small"
              class="asset-tag"
            >
              {{ tag.tagName }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="history-card">
      <template #header>
        <div class="history-header">
          <span>迁移批次记录</span>
          <el-button size="small" @click="loadBatches">刷新</el-button>
        </div>
      </template>
      <el-table :data="batches" border>
        <el-table-column prop="batchNo" label="批次号" width="200" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="batchStatusTag(scope.row.status)" size="small">
              {{ batchStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetAreaName" label="目标分区" width="140" />
        <el-table-column label="迁移数量" width="160">
          <template #default="scope">
            共 {{ scope.row.totalCount }} / 成功 {{ scope.row.successCount }} / 失败 {{ scope.row.failCount }}
          </template>
        </el-table-column>
        <el-table-column prop="changeReason" label="变更原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button size="small" link type="primary" @click="openBatchDetail(scope.row.id)">查看明细</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="batches.length === 0" description="暂无迁移批次" />
    </el-card>

    <!-- 批量调区对话框 -->
    <el-dialog v-model="transferDialogVisible" title="批量调整分区" width="860px" @closed="handleTransferClosed">
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="100px">
        <el-form-item label="目标分区" prop="targetAreaId">
          <el-select
            v-model="transferForm.targetAreaId"
            placeholder="请选择目标分区"
            style="width: 320px"
            @change="preview = null"
          >
            <el-option
              v-for="area in targetAreaOptions"
              :key="area.id"
              :label="area.areaName"
              :value="area.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" prop="changeReason">
          <el-input
            v-model="transferForm.changeReason"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="请填写变更原因（必填）"
          />
        </el-form-item>
        <el-form-item label="操作人" prop="operator">
          <el-input v-model="transferForm.operator" maxlength="100" placeholder="请填写操作人（必填）" style="width: 320px" />
        </el-form-item>
      </el-form>

      <div v-if="preview" class="preview-block">
        <el-divider content-position="left">迁移预览（提交前确认）</el-divider>

        <el-alert
          v-if="preview.invalidCount > 0"
          type="error"
          :closable="false"
          show-icon
          class="preview-alert"
          :title="`有 ${preview.invalidCount} 项不可迁移（资产失效 ${preview.invalidCount - preview.alreadyInTargetCount} 项，已在目标分区 ${preview.alreadyInTargetCount} 项），整批不可提交，请调整勾选`"
        />
        <el-alert
          type="success"
          :closable="false"
          show-icon
          class="preview-alert"
          :title="`勾选 ${preview.selectedCount} 张，本次将迁移 ${preview.moveCount} 张桌椅到【${preview.targetAreaName}】`"
        />

        <div class="ownership-summary">
          <div class="summary-title">原新归属汇总：</div>
          <el-tag
            v-for="group in ownershipGroups"
            :key="group.oldAreaName"
            type="info"
            size="large"
            class="ownership-tag"
          >
            {{ group.oldAreaName }} → {{ preview.targetAreaName }}：{{ group.count }} 张
          </el-tag>
        </div>

        <el-table :data="preview.items" border size="small" max-height="280">
          <el-table-column prop="assetCode" label="资产编号" width="110">
            <template #default="scope">{{ scope.row.assetCode || `#${scope.row.deskChairId}` }}</template>
          </el-table-column>
          <el-table-column label="原归属" width="150">
            <template #default="scope">{{ scope.row.oldAreaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="新归属" width="150">
            <template #default="scope">{{ scope.row.newAreaName }}</template>
          </el-table-column>
          <el-table-column label="校验结果" min-width="160">
            <template #default="scope">
              <el-tag v-if="scope.row.valid" type="success" size="small">可迁移</el-tag>
              <el-tag v-else type="danger" size="small">{{ scope.row.errorMessage }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button :loading="previewLoading" @click="handlePreview">预览迁移</el-button>
        <el-button
          type="primary"
          :disabled="!preview || !preview.canSubmit"
          :loading="executeLoading"
          @click="handleExecute"
        >
          确认提交（整批执行，失败回滚）
        </el-button>
      </template>
    </el-dialog>

    <!-- 批次结果对话框 -->
    <el-dialog v-model="resultDialogVisible" title="批次执行结果" width="820px">
      <template v-if="result">
        <el-result
          :icon="result.status === 'SUCCESS' ? 'success' : 'error'"
          :title="result.status === 'SUCCESS' ? '整批迁移成功' : '整批未执行或已回滚'"
          :subTitle="`批次号：${result.batchNo}`"
        />
        <el-descriptions :column="2" border class="result-desc">
          <el-descriptions-item label="批次号">
            <span class="batch-no">{{ result.batchNo }}</span>
            <el-button link type="primary" size="small" @click="copyBatchNo">复制</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="batchStatusTag(result.status)" size="small">
              {{ batchStatusText(result.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标分区">{{ result.targetAreaName }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ result.operator }}</el-descriptions-item>
          <el-descriptions-item label="迁移数量">
            共 {{ result.totalCount }} 张，成功 {{ result.successCount }} 张，失败 {{ result.failCount }} 张
          </el-descriptions-item>
          <el-descriptions-item label="执行时间">{{ formatTime(result.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="变更原因" :span="2">{{ result.changeReason }}</el-descriptions-item>
          <el-descriptions-item v-if="result.errorMessage" label="失败原因" :span="2">
            <span class="error-text">{{ result.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="result.items" border size="small" max-height="260" class="result-items">
          <el-table-column prop="assetCode" label="资产编号" width="110" />
          <el-table-column label="原归属" width="150">
            <template #default="scope">{{ scope.row.oldAreaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="新归属" width="150">
            <template #default="scope">{{ scope.row.newAreaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="结果" min-width="180">
            <template #default="scope">
              <el-tag v-if="scope.row.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
              <el-tag v-else type="danger" size="small">{{ scope.row.errorMessage || '失败' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批次明细对话框 -->
    <el-dialog v-model="detailDialogVisible" title="批次明细" width="820px">
      <template v-if="batchDetail">
        <el-descriptions :column="2" border class="result-desc">
          <el-descriptions-item label="批次号">{{ batchDetail.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="batchStatusTag(batchDetail.status)" size="small">
              {{ batchStatusText(batchDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标分区">{{ batchDetail.targetAreaName }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ batchDetail.operator }}</el-descriptions-item>
          <el-descriptions-item label="迁移数量">
            共 {{ batchDetail.totalCount }} 张，成功 {{ batchDetail.successCount }} 张，失败 {{ batchDetail.failCount }} 张
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(batchDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="变更原因" :span="2">{{ batchDetail.changeReason }}</el-descriptions-item>
          <el-descriptions-item v-if="batchDetail.errorMessage" label="失败原因" :span="2">
            <span class="error-text">{{ batchDetail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="batchDetail.items" border size="small" max-height="360" class="result-items">
          <el-table-column prop="assetCode" label="资产编号" width="110">
            <template #default="scope">{{ scope.row.assetCode || `#${scope.row.deskChairId}` }}</template>
          </el-table-column>
          <el-table-column label="原归属" width="150">
            <template #default="scope">{{ scope.row.oldAreaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="新归属" width="150">
            <template #default="scope">{{ scope.row.newAreaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="结果" min-width="160">
            <template #default="scope">
              <el-tag v-if="scope.row.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
              <el-tag v-else type="danger" size="small">{{ scope.row.errorMessage || '失败' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { deskChairApi, readingAreaApi, tagApi, batchApi } from '../api'

const deskChairs = ref([])
const readingAreas = ref([])
const tags = ref([])
const batches = ref([])
const deskTableRef = ref()

const filters = reactive({
  areaId: null,
  tagIds: []
})

const selectedRows = ref([])

const transferDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const previewLoading = ref(false)
const executeLoading = ref(false)
const transferFormRef = ref()

const transferForm = reactive({
  targetAreaId: null,
  changeReason: '',
  operator: ''
})

const transferRules = {
  targetAreaId: [{ required: true, message: '请选择目标分区', trigger: 'change' }],
  changeReason: [{ required: true, message: '请填写变更原因', trigger: 'blur' }],
  operator: [{ required: true, message: '请填写操作人', trigger: 'blur' }]
}

const preview = ref(null)
const result = ref(null)
const batchDetail = ref(null)

const targetAreaOptions = computed(() =>
  readingAreas.value.filter(area => area.status === 1 || area.status === undefined)
)

const ownershipGroups = computed(() => {
  if (!preview.value) return []
  const groups = {}
  preview.value.items
    .filter(item => item.valid)
    .forEach(item => {
      const key = item.oldAreaName || '未分区'
      groups[key] = (groups[key] || 0) + 1
    })
  return Object.keys(groups).map(oldAreaName => ({ oldAreaName, count: groups[oldAreaName] }))
})

const loadDeskChairs = async () => {
  deskChairs.value = await deskChairApi.search({
    areaId: filters.areaId,
    tagIds: filters.tagIds
  })
}

const loadAreas = async () => {
  readingAreas.value = await readingAreaApi.getAll()
}

const loadTags = async () => {
  tags.value = await tagApi.getAll()
}

const loadBatches = async () => {
  batches.value = await batchApi.getAll()
}

const handleFilter = async () => {
  await loadDeskChairs()
}

const resetFilter = async () => {
  filters.areaId = null
  filters.tagIds = []
  await loadDeskChairs()
}

const handleSelectionChange = rows => {
  selectedRows.value = rows
}

const clearSelection = () => {
  deskTableRef.value?.clearSelection()
}

const openTransferDialog = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选需要调区的桌椅')
    return
  }
  transferForm.targetAreaId = null
  transferForm.changeReason = ''
  transferForm.operator = ''
  preview.value = null
  transferDialogVisible.value = true
}

const handleTransferClosed = () => {
  preview.value = null
  transferFormRef.value?.clearValidate()
}

const buildPayload = () => ({
  deskChairIds: selectedRows.value.map(row => row.id),
  targetAreaId: transferForm.targetAreaId,
  changeReason: transferForm.changeReason,
  operator: transferForm.operator
})

const handlePreview = async () => {
  try {
    await transferFormRef.value.validate()
  } catch (e) {
    return
  }
  previewLoading.value = true
  try {
    preview.value = await batchApi.preview(buildPayload())
    if (!preview.value.canSubmit) {
      ElMessage.error('存在不可迁移资产，请调整勾选后再提交')
    }
  } catch (e) {
    ElMessage.error(e.message || '预览失败')
  } finally {
    previewLoading.value = false
  }
}

const handleExecute = async () => {
  try {
    await transferFormRef.value.validate()
  } catch (e) {
    return
  }
  executeLoading.value = true
  try {
    const res = await batchApi.execute(buildPayload())
    result.value = res
    transferDialogVisible.value = false
    resultDialogVisible.value = true
    if (res.status === 'SUCCESS') {
      ElMessage.success(`批次 ${res.batchNo} 整批迁移成功`)
      clearSelection()
      await loadDeskChairs()
    } else {
      ElMessage.error('整批未执行或已回滚，详见批次结果')
    }
    await loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '执行失败')
  } finally {
    executeLoading.value = false
  }
}

const copyBatchNo = async () => {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(result.value.batchNo)
    ElMessage.success('批次号已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const openBatchDetail = async id => {
  try {
    batchDetail.value = await batchApi.getById(id)
    detailDialogVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载明细失败')
  }
}

const batchStatusTag = status => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

const batchStatusText = status => {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败/回滚'
  return '处理中'
}

const formatTime = time => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAreas()
  loadTags()
  loadDeskChairs()
  loadBatches()
})
</script>

<style scoped>
.page-container {
  max-width: 1400px;
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
.table-card,
.history-card {
  margin-bottom: 20px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.selection-info {
  font-size: 14px;
}

.selection-count {
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.selection-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 6px;
}

.asset-tag {
  margin-right: 4px;
  margin-bottom: 2px;
  color: #fff;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-block {
  margin-top: 8px;
}

.preview-alert {
  margin-bottom: 10px;
}

.ownership-summary {
  margin: 12px 0;
}

.summary-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.ownership-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.result-desc {
  margin: 0 8px 16px;
}

.result-items {
  margin: 0 8px;
}

.batch-no {
  font-family: 'SFMono-Regular', Consolas, monospace;
  margin-right: 6px;
}

.error-text {
  color: #f56c6c;
}
</style>
