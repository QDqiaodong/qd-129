<template>
  <div class="page-container">
    <div class="page-header">
      <h2>夜间巡检</h2>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建巡检批次</el-button>
    </div>

    <!-- 批次列表 -->
    <el-card class="batch-card">
      <template #header>
        <div class="card-header">
          <span>巡检批次</span>
          <el-button size="small" @click="loadBatches">刷新</el-button>
        </div>
      </template>
      <el-table :data="batches" border>
        <el-table-column prop="batchNo" label="批次号" width="190" />
        <el-table-column label="巡检分区" min-width="130">
          <template #default="scope">
            {{ scope.row.areaName }}（{{ scope.row.areaCode }}）
          </template>
        </el-table-column>
        <el-table-column label="应巡/已巡" width="110">
          <template #default="scope">
            <span class="count-group">
              {{ scope.row.totalCount }} /
              <span :class="{ 'count-done': scope.row.status === 'COMPLETED' }">{{ scope.row.checkedCount }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="问题" width="80">
          <template #default="scope">
            <el-badge :value="scope.row.problemCount" :hidden="scope.row.problemCount === 0" type="danger">
              <span style="display:inline-block;width:30px"></span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
              {{ scope.row.status === 'COMPLETED' ? '已结束' : '巡检中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="值班员" width="90" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="创建时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button size="small" link type="primary" @click="openDetail(scope.row.id)">
              {{ scope.row.status === 'COMPLETED' ? '查看详情' : '巡检登记' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="batches.length === 0" description="暂无巡检批次" />
    </el-card>

    <!-- 巡检记录：按分区和是否有问题筛出 -->
    <el-card class="record-card">
      <template #header>
        <div class="card-header">
          <span>巡检记录</span>
          <el-button size="small" @click="loadRecords">刷新</el-button>
        </div>
      </template>
      <el-form :inline="true" class="record-filter">
        <el-form-item label="阅览分区">
          <el-select v-model="recordFilter.areaId" placeholder="全部分区" clearable style="width: 200px" @change="loadRecords">
            <el-option v-for="area in readingAreas" :key="area.id" :label="area.areaName" :value="area.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否有问题">
          <el-select v-model="recordFilter.hasProblem" placeholder="全部" style="width: 140px" @change="loadRecords">
            <el-option label="全部" value="" />
            <el-option label="有问题" :value="1" />
            <el-option label="无问题" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="records" border size="small">
        <el-table-column prop="assetCode" label="资产编号" width="100" />
        <el-table-column label="所属分区" min-width="120">
          <template #default="scope">
            {{ scope.row.areaName }}（{{ scope.row.areaCode }}）
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次号" width="180" />
        <el-table-column label="灯" width="80">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.lightResult)" size="small">
              {{ resultText('light', scope.row.lightResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="插座" width="80">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.socketResult)" size="small">
              {{ resultText('socket', scope.row.socketResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="桌面" width="100">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.deskSurfaceResult)" size="small">
              {{ resultText('deskSurface', scope.row.deskSurfaceResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否有问题" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.hasProblem === 1 ? 'danger' : 'success'" size="small">
              {{ scope.row.hasProblem === 1 ? '有问题' : '无问题' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理意见" min-width="180">
          <template #default="scope">
            <div>{{ scope.row.handleOpinion || '-' }}</div>
            <div v-if="scope.row.problemDetail" class="problem-detail">{{ scope.row.problemDetail }}</div>
          </template>
        </el-table-column>
        <el-table-column label="巡检人/时间" width="170">
          <template #default="scope">
            <div>{{ scope.row.checkedBy || '-' }}</div>
            <div class="meta-time">{{ formatTime(scope.row.checkedAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button size="small" link type="primary" @click="openHistory(scope.row)">同件记录</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="records.length === 0" description="暂无巡检记录" :image-size="60" />
    </el-card>

    <!-- 新建批次 -->
    <el-dialog v-model="createVisible" title="新建夜间巡检批次" width="480px">
      <el-alert
        title="开批后系统将把该分区在册桌椅（含停用）全部列为待巡明细，需逐件登记灯、插座和桌面情况。"
        type="info"
        :closable="false"
        show-icon
        class="create-alert"
      />
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="巡检分区" prop="areaId">
          <el-select v-model="createForm.areaId" placeholder="请选择阅览分区" style="width: 100%">
            <el-option
              v-for="area in areaOptions"
              :key="area.id"
              :label="`${area.areaName}（${area.areaCode}）`"
              :value="area.id"
            />
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
        <el-button type="primary" :loading="createLoading" @click="handleCreate">创建并开始巡检</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情 / 巡检登记 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail ? `巡检批次 ${detail.batchNo}` : '巡检详情'"
      size="88%"
      destroy-on-close
    >
      <template v-if="detail">
        <el-descriptions :column="4" border size="small" class="detail-desc">
          <el-descriptions-item label="巡检分区">
            {{ detail.areaName }}（{{ detail.areaCode }}）
          </el-descriptions-item>
          <el-descriptions-item label="批次状态">
            <el-tag :type="detail.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
              {{ detail.status === 'COMPLETED' ? '已结束' : '巡检中' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="值班员">{{ detail.operator }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="应巡数量">{{ detail.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="已巡数量">{{ detail.checkedCount }} / {{ detail.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="问题数量">
            <span :class="detail.problemCount > 0 ? 'problem-text' : ''">{{ detail.problemCount }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.completedAt" label="结束时间">
            {{ formatTime(detail.completedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="detail.completedAt ? 3 : 4">
            {{ detail.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.status === 'COMPLETED'"
          type="success"
          :closable="false"
          show-icon
          title="该批次已结束，明细只读；同一件桌椅的历史巡检可在“巡检记录”列表点开查看。"
          class="lock-alert"
        />

        <!-- 巡检中操作条 -->
        <div v-if="detail.status === 'OPEN'" class="action-bar">
          <el-progress
            :percentage="progressPercent"
            :status="detail.checkedCount === detail.totalCount ? 'success' : undefined"
            class="progress"
          />
          <div class="action-row">
            <el-input
              v-model="entryOperator"
              size="small"
              class="operator-input"
              placeholder="巡检登记人"
              maxlength="100"
            />
            <span class="pending-hint">待巡 {{ pendingItems.length }} 件</span>
            <el-button
              type="success"
              :loading="completeLoading"
              @click="handleComplete"
            >
              结束批次（{{ detail.checkedCount }}/{{ detail.totalCount }} 已巡）
            </el-button>
          </div>
        </div>

        <!-- 逐件登记表：灯/插座/桌面等编辑控件仅在巡检中（isOpen）渲染，结束后为纯文本只读 -->
        <el-table :data="detail.items" border size="small" class="item-table">
          <el-table-column prop="assetCode" label="资产编号" width="100" fixed />
          <el-table-column label="灯" :width="isOpen ? 130 : 90">
            <template #default="scope">
              <el-select
                v-if="isOpen"
                v-model="entryForms[scope.row.id].lightResult"
                size="small"
                placeholder="未登记"
              >
                <el-option label="正常" value="NORMAL" />
                <el-option label="不亮" value="ABNORMAL" />
              </el-select>
              <el-tag v-else :type="resultTagType(scope.row.lightResult)" size="small">
                {{ resultText('light', scope.row.lightResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="插座" :width="isOpen ? 130 : 90">
            <template #default="scope">
              <el-select
                v-if="isOpen"
                v-model="entryForms[scope.row.id].socketResult"
                size="small"
                placeholder="未登记"
              >
                <el-option label="正常" value="NORMAL" />
                <el-option label="失灵" value="ABNORMAL" />
              </el-select>
              <el-tag v-else :type="resultTagType(scope.row.socketResult)" size="small">
                {{ resultText('socket', scope.row.socketResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="桌面" :width="isOpen ? 130 : 100">
            <template #default="scope">
              <el-select
                v-if="isOpen"
                v-model="entryForms[scope.row.id].deskSurfaceResult"
                size="small"
                placeholder="未登记"
              >
                <el-option label="正常" value="NORMAL" />
                <el-option label="涂鸦/破损" value="ABNORMAL" />
              </el-select>
              <el-tag v-else :type="resultTagType(scope.row.deskSurfaceResult)" size="small">
                {{ resultText('deskSurface', scope.row.deskSurfaceResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="问题描述" min-width="170">
            <template #default="scope">
              <el-input
                v-if="isOpen"
                v-model="entryForms[scope.row.id].problemDetail"
                size="small"
                maxlength="200"
                placeholder="问题补充（可选）"
              />
              <span v-else>{{ scope.row.problemDetail || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="处理意见" min-width="190">
            <template #default="scope">
              <template v-if="isOpen">
                <el-input
                  v-model="entryForms[scope.row.id].handleOpinion"
                  size="small"
                  maxlength="200"
                  :placeholder="rowHasProblem(scope.row.id) ? '有问题必填' : '无问题可留空'"
                  :class="{ 'opinion-required': rowHasProblem(scope.row.id) && !entryForms[scope.row.id].handleOpinion.trim() }"
                />
              </template>
              <span v-else>{{ scope.row.handleOpinion || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.checkStatus === 'CHECKED' ? 'success' : 'info'" size="small">
                {{ scope.row.checkStatus === 'CHECKED' ? '已巡' : '待巡' }}
              </el-tag>
              <el-tag v-if="scope.row.checkStatus === 'CHECKED' && scope.row.hasProblem === 1" type="danger" size="small" class="problem-tag">
                有问题
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" :width="isOpen ? 90 : 110" fixed="right">
            <template #default="scope">
              <el-button
                v-if="isOpen"
                link
                type="primary"
                size="small"
                :loading="submittingId === scope.row.id"
                @click="submitItem(scope.row)"
              >{{ scope.row.checkStatus === 'CHECKED' ? '重新提交' : '提交该件' }}</el-button>
              <el-button v-else link type="info" size="small" @click="openHistory(scope.row)">同件记录</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 同一件桌椅的巡检记录 -->
    <el-dialog
      v-model="historyVisible"
      :title="`巡检记录（资产编号：${historyAssetCode}）`"
      width="760px"
      append-to-body
    >
      <el-table :data="historyRecords" border size="small" v-loading="historyLoading">
        <el-table-column prop="batchNo" label="批次号" width="180" />
        <el-table-column label="批次状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.batchStatus === 'COMPLETED' ? 'success' : 'warning'" size="small">
              {{ scope.row.batchStatus === 'COMPLETED' ? '已结束' : '巡检中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="灯" width="70">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.lightResult)" size="small">
              {{ resultText('light', scope.row.lightResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="插座" width="70">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.socketResult)" size="small">
              {{ resultText('socket', scope.row.socketResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="桌面" width="90">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.deskSurfaceResult)" size="small">
              {{ resultText('deskSurface', scope.row.deskSurfaceResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否有问题" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.hasProblem === 1 ? 'danger' : 'success'" size="small">
              {{ scope.row.hasProblem === 1 ? '有问题' : '无问题' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理意见" min-width="150">
          <template #default="scope">{{ scope.row.handleOpinion || '-' }}</template>
        </el-table-column>
        <el-table-column label="巡检人/时间" width="160">
          <template #default="scope">
            <div>{{ scope.row.checkedBy || '-' }}</div>
            <div class="meta-time">{{ formatTime(scope.row.checkedAt) }}</div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!historyLoading && historyRecords.length === 0" description="该件暂无巡检记录" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { nightInspectionApi, readingAreaApi } from '../api'
import { validateInspectionEntry, hasInspectionProblem, canCompleteBatch, resultText } from '../utils/nightInspection'

const batches = ref([])
const records = ref([])
const readingAreas = ref([])

const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const createForm = reactive({ areaId: null, operator: '', remark: '' })
const createRules = {
  areaId: [{ required: true, message: '请选择巡检分区', trigger: 'change' }],
  operator: [{ required: true, message: '请填写值班员', trigger: 'blur' }]
}

const detailVisible = ref(false)
const detail = ref(null)
const entryForms = ref({})
const entryOperator = ref('')
const submittingId = ref(null)
const completeLoading = ref(false)

const recordFilter = reactive({ areaId: null, hasProblem: '' })

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRecords = ref([])
const historyAssetCode = ref('')

const areaOptions = computed(() =>
  readingAreas.value.filter(area => area.status === 1 || area.status === undefined)
)

const isOpen = computed(() => detail.value && detail.value.status === 'OPEN')

const pendingItems = computed(() =>
  detail.value ? detail.value.items.filter(i => i.checkStatus !== 'CHECKED') : []
)

const progressPercent = computed(() => {
  if (!detail.value || !detail.value.totalCount) return 0
  return Math.round((detail.value.checkedCount / detail.value.totalCount) * 100)
})

const rowHasProblem = itemId => hasInspectionProblem(entryForms.value[itemId])

const loadBatches = async () => {
  batches.value = await nightInspectionApi.listBatches()
}

const loadAreas = async () => {
  readingAreas.value = await readingAreaApi.getAll()
}

const loadRecords = async () => {
  records.value = await nightInspectionApi.listRecords({
    areaId: recordFilter.areaId || undefined,
    hasProblem: recordFilter.hasProblem === '' ? undefined : recordFilter.hasProblem
  })
}

const openCreateDialog = () => {
  createForm.areaId = null
  createForm.operator = ''
  createForm.remark = ''
  createVisible.value = true
}

const handleCreate = async () => {
  try {
    await createFormRef.value.validate()
  } catch (e) {
    return
  }
  createLoading.value = true
  try {
    const batch = await nightInspectionApi.createBatch({ ...createForm })
    ElMessage.success(`巡检批次 ${batch.batchNo} 已创建，共 ${batch.totalCount} 件待巡`)
    createVisible.value = false
    await loadBatches()
    await openDetail(batch.id)
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    createLoading.value = false
  }
}

const initEntryForms = () => {
  const forms = {}
  detail.value.items.forEach(item => {
    forms[item.id] = {
      lightResult: item.lightResult || '',
      socketResult: item.socketResult || '',
      deskSurfaceResult: item.deskSurfaceResult || '',
      problemDetail: item.problemDetail || '',
      handleOpinion: item.handleOpinion || ''
    }
  })
  entryForms.value = forms
}

const openDetail = async id => {
  try {
    detail.value = await nightInspectionApi.getBatch(id)
    initEntryForms()
    entryOperator.value = detail.value.operator || ''
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载巡检详情失败')
  }
}

const submitItem = async item => {
  // 结束后只读：控件虽已隐藏，仍拦住任何在途/陈旧状态触发的再提交
  if (!isOpen.value) {
    ElMessage.warning('该巡检批次已结束，明细只读，不能再登记或修改')
    await reopenDetail()
    return
  }
  const form = entryForms.value[item.id]
  const error = validateInspectionEntry(form)
  if (error) {
    ElMessage.error(error)
    return
  }
  if (!entryOperator.value.trim()) {
    ElMessage.error('请填写巡检登记人')
    return
  }
  submittingId.value = item.id
  try {
    detail.value = await nightInspectionApi.checkItem(detail.value.id, item.id, {
      lightResult: form.lightResult,
      socketResult: form.socketResult,
      deskSurfaceResult: form.deskSurfaceResult,
      problemDetail: form.problemDetail,
      handleOpinion: form.handleOpinion,
      operator: entryOperator.value
    })
    initEntryForms()
    ElMessage.success(`${item.assetCode} 已登记`)
    await loadBatches()
    await loadRecords()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
    // 批次可能恰好在本请求在途期间被结束：回刷详情，界面立即切成只读，避免继续误改
    if (isBatchClosedError(e)) await reopenDetail()
  } finally {
    submittingId.value = null
  }
}

const handleComplete = async () => {
  if (!isOpen.value) {
    ElMessage.warning('该巡检批次已结束，不能重复结束')
    await reopenDetail()
    return
  }
  if (!canCompleteBatch(detail.value)) {
    ElMessage.error(`仍有 ${pendingItems.value.length} 件桌椅未巡检，批次没巡完不能结束`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `全部 ${detail.value.totalCount} 件均已巡检登记（问题 ${detail.value.problemCount} 件），结束后批次将锁定，不可再登记。确认结束？`,
      '结束巡检批次',
      { type: 'warning', confirmButtonText: '结束批次', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  completeLoading.value = true
  try {
    detail.value = await nightInspectionApi.completeBatch(detail.value.id, {
      operator: entryOperator.value || detail.value.operator
    })
    initEntryForms()
    ElMessage.success('巡检批次已结束')
    await loadBatches()
    await loadRecords()
  } catch (e) {
    ElMessage.error(e.message || '结束失败')
    if (isBatchClosedError(e)) await reopenDetail()
  } finally {
    completeLoading.value = false
  }
}

// 后端以 400 + “已结束”文案拒绝并发/越权写入
const isBatchClosedError = e => /已结束/.test(e?.message || '')

// 重新拉取当前批次并同步登记表单，确保界面与后端锁定状态一致（只读）
const reopenDetail = async () => {
  if (!detail.value) return
  try {
    detail.value = await nightInspectionApi.getBatch(detail.value.id)
    initEntryForms()
    entryOperator.value = detail.value.operator || ''
    await loadBatches()
    await loadRecords()
  } catch (e) {
    ElMessage.error(e.message || '刷新巡检详情失败')
  }
}

const openHistory = async row => {
  historyAssetCode.value = row.assetCode
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyRecords.value = await nightInspectionApi.getDeskChairRecords(row.deskChairId)
  } catch (e) {
    ElMessage.error(e.message || '加载巡检记录失败')
  } finally {
    historyLoading.value = false
  }
}

const resultTagType = result => {
  if (!result) return 'info'
  return result === 'NORMAL' ? 'success' : 'danger'
}

const formatTime = time => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadAreas()
  loadBatches()
  loadRecords()
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

.batch-card {
  margin-bottom: 20px;
}

.record-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.record-filter {
  margin-bottom: 4px;
}

.count-group {
  font-variant-numeric: tabular-nums;
}

.count-done {
  color: #67c23a;
  font-weight: 600;
}

.detail-desc {
  margin-bottom: 16px;
}

.problem-text {
  color: #f56c6c;
  font-weight: 600;
}

.lock-alert {
  margin-bottom: 16px;
}

.create-alert {
  margin-bottom: 14px;
}

.action-bar {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

.progress {
  margin-bottom: 12px;
}

.action-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.operator-input {
  width: 160px;
}

.pending-hint {
  color: #e6a23c;
  font-size: 13px;
  flex: 1;
}

.item-table {
  margin-bottom: 24px;
}

.problem-tag {
  margin-left: 4px;
}

.problem-detail {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.meta-time {
  font-size: 12px;
  color: #909399;
}

.opinion-required :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
</style>
