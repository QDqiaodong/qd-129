<template>
  <div class="page-container">
    <div class="page-header">
      <h2>阅览桌椅盘点</h2>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建盘点批次</el-button>
    </div>

    <!-- 批次列表 -->
    <el-card class="batch-card">
      <template #header>
        <div class="card-header">
          <span>盘点批次</span>
          <el-button size="small" @click="loadBatches">刷新</el-button>
        </div>
      </template>
      <el-table :data="batches" border>
        <el-table-column prop="batchNo" label="批次号" width="190" />
        <el-table-column label="盘点分区" min-width="130">
          <template #default="scope">
            {{ scope.row.areaName }}（{{ scope.row.areaCode }}）
          </template>
        </el-table-column>
        <el-table-column label="应盘/实盘/已核" width="150">
          <template #default="scope">
            <span class="count-group">
              {{ scope.row.expectedCount }} / {{ scope.row.actualCount }} /
              <span :class="{ 'count-done': scope.row.status === 'COMPLETED' }">{{ scope.row.checkedCount }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异" width="80">
          <template #default="scope">
            <el-badge :value="scope.row.diffCount" :hidden="scope.row.diffCount === 0" type="danger">
              <span style="display:inline-block;width:30px"></span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
              {{ scope.row.status === 'COMPLETED' ? '已完成' : '盘点中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="创建时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button size="small" link type="primary" @click="openDetail(scope.row.id)">
              {{ scope.row.status === 'COMPLETED' ? '查看详情' : '盘点处理' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="batches.length === 0" description="暂无盘点批次" />
    </el-card>

    <!-- 新建批次 -->
    <el-dialog v-model="createVisible" title="新建盘点批次" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="盘点分区" prop="areaId">
          <el-select v-model="createForm.areaId" placeholder="请选择阅览分区" style="width: 100%">
            <el-option
              v-for="area in areaOptions"
              :key="area.id"
              :label="`${area.areaName}（${area.areaCode}）`"
              :value="area.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人" prop="operator">
          <el-input v-model="createForm.operator" maxlength="100" placeholder="请填写操作人" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">创建并进入盘点</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情 / 盘点处理 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail ? `盘点批次 ${detail.batchNo}` : '盘点详情'"
      size="86%"
      destroy-on-close
    >
      <template v-if="detail">
        <!-- 概览 -->
        <el-descriptions :column="4" border size="small" class="detail-desc">
          <el-descriptions-item label="盘点分区">
            {{ detail.areaName }}（{{ detail.areaCode }}）
          </el-descriptions-item>
          <el-descriptions-item label="批次状态">
            <el-tag :type="detail.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
              {{ detail.status === 'COMPLETED' ? '已完成' : '盘点中' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operator }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="应盘数量">{{ detail.expectedCount }}</el-descriptions-item>
          <el-descriptions-item label="实盘数量">{{ detail.actualCount }}</el-descriptions-item>
          <el-descriptions-item label="已核数量">{{ detail.checkedCount }} / {{ detail.items.length }}</el-descriptions-item>
          <el-descriptions-item label="差异数量">
            <span :class="detail.diffCount > 0 ? 'diff-text' : ''">{{ detail.diffCount }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.completedAt" label="完成时间">
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
          title="该批次已完成并闭环，明细只读，不可再录入、确认或复核。"
          class="lock-alert"
        />

        <!-- 盘点中操作条 -->
        <div v-if="detail.status === 'OPEN'" class="action-bar">
          <el-tabs v-model="entryMode" class="entry-tabs">
            <el-tab-pane label="手动录入" name="manual" />
            <el-tab-pane label="粘贴导入（CSV/TSV）" name="import" />
          </el-tabs>

          <div v-if="entryMode === 'manual'" class="manual-entry">
            <el-table :data="manualLines" border size="small">
              <el-table-column label="资产编号" width="150">
                <template #default="scope">
                  <el-input v-model="scope.row.assetCode" size="small" placeholder="如 DC001" />
                </template>
              </el-table-column>
              <el-table-column label="实盘分区（留空=本分区）" width="200">
                <template #default="scope">
                  <el-select v-model="scope.row.actualAreaId" size="small" clearable placeholder="默认盘点分区">
                    <el-option
                      v-for="area in readingAreas"
                      :key="area.id"
                      :label="area.areaName"
                      :value="area.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="启用状态（留空=在册）" width="170">
                <template #default="scope">
                  <el-select v-model="scope.row.actualStatus" size="small" clearable placeholder="默认在册状态">
                    <el-option label="启用" :value="1" />
                    <el-option label="停用" :value="0" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="实盘标签（不选=未核对）" min-width="220">
                <template #default="scope">
                  <el-select
                    v-model="scope.row.actualTagIds"
                    size="small"
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    clearable
                    placeholder="未核对标签"
                  >
                    <el-option
                      v-for="tag in activeTags"
                      :key="tag.id"
                      :label="tag.tagName"
                      :value="tag.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column width="70">
                <template #default="scope">
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :disabled="manualLines.length === 1"
                    @click="removeManualLine(scope.$index)"
                  >删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" class="add-line-btn" @click="addManualLine">+ 增加一行</el-button>
          </div>

          <div v-else class="import-entry">
            <el-input
              v-model="importText"
              type="textarea"
              :rows="6"
              placeholder="每行一件资产，字段顺序：资产编号,实盘分区(编码/名称，可空),启用状态(启用/停用/1/0，可空),标签(多个用顿号分隔名称，可空)。首行可写表头。&#10;示例：&#10;DC001,第一阅览区,启用,自习专用、双人桌&#10;DC005,,停用,无标签"
            />
            <div class="import-meta">
              <span>已解析 <b>{{ parsedPreview.lines.length }}</b> 行</span>
              <el-button size="small" @click="previewImport">解析预览</el-button>
            </div>
            <el-alert
              v-for="(err, i) in parsedPreview.errors"
              :key="i"
              type="error"
              :title="err"
              :closable="false"
              show-icon
              class="import-error"
            />
          </div>

          <div class="submit-bar">
            <el-input
              v-model="submitOperator"
              size="small"
              class="submit-operator"
              placeholder="操作人"
              maxlength="100"
            />
            <el-input
              v-model="submitRemark"
              size="small"
              class="submit-remark"
              placeholder="本轮录入说明（可选，记入处理记录）"
              maxlength="200"
            />
            <el-button
              type="primary"
              :loading="submitLoading"
              @click="handleSubmit"
            >
              {{ detail.items.length ? '重新录入并比对（替换本轮明细）' : '提交实盘并比对差异' }}
            </el-button>
            <el-button
              type="success"
              :disabled="!detail.items.length"
              :loading="completeLoading"
              @click="handleComplete"
            >
              完成批次（{{ detail.checkedCount }}/{{ detail.items.length }} 已核）
            </el-button>
          </div>
        </div>

        <!-- 差异明细 -->
        <div class="item-toolbar">
          <el-radio-group v-model="itemFilter" size="small">
            <el-radio-button label="ALL">全部明细（{{ detail.items.length }}）</el-radio-button>
            <el-radio-button label="MISSING">
              仅缺失（{{ missingItems.length }}）
            </el-radio-button>
            <el-radio-button label="MISSING_NO_REASON">
              缺缺失原因（{{ missingWithoutReasonItems.length }}）
            </el-radio-button>
          </el-radio-group>
          <el-button
            v-if="detail.status === 'OPEN' && missingWithoutReasonItems.length > 0"
            size="small"
            type="danger"
            plain
            @click="promptMissingReasonBlock"
          >
            {{ missingWithoutReasonItems.length }} 件缺失未写原因，点此查看
          </el-button>
        </div>
        <el-table :data="filteredItems" border size="small" class="item-table">
          <el-table-column prop="assetCode" label="资产编号" width="100" fixed />
          <el-table-column label="差异类型" width="100" fixed>
            <template #default="scope">
              <el-tag :type="diffTagType(scope.row.diffType)" size="small">
                {{ diffText(scope.row.diffType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="在册分区" width="110">
            <template #default="scope">{{ scope.row.bookAreaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="实盘分区" width="110">
            <template #default="scope">{{ scope.row.actualAreaName || '-' }}</template>
          </el-table-column>
          <el-table-column label="在册状态" width="80">
            <template #default="scope">{{ scope.row.bookStatus === null ? '-' : statusText(scope.row.bookStatus) }}</template>
          </el-table-column>
          <el-table-column label="实盘状态" width="80">
            <template #default="scope">{{ scope.row.actualStatus === null || scope.row.actualStatus === undefined ? '-' : statusText(scope.row.actualStatus) }}</template>
          </el-table-column>
          <el-table-column label="在册标签" min-width="130">
            <template #default="scope">{{ scope.row.bookTagNames || '-' }}</template>
          </el-table-column>
          <el-table-column label="实盘标签" min-width="130">
            <template #default="scope">{{ scope.row.actualTagNames || (scope.row.diffType === 'MISSING' ? '-' : '未核对') }}</template>
          </el-table-column>
          <el-table-column label="核对状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.checkStatus === 'CONFIRMED' ? 'success' : 'info'" size="small">
                {{ scope.row.checkStatus === 'CONFIRMED' ? '已确认' : '待核' }}
              </el-tag>
              <span v-if="scope.row.recheckCount > 0" class="recheck-badge">复核{{ scope.row.recheckCount }}次</span>
            </template>
          </el-table-column>
          <el-table-column label="缺失原因/处理意见" min-width="180">
            <template #default="scope">
              <template v-if="scope.row.diffType === 'MISSING'">
                <div v-if="scope.row.missingReason" class="missing-reason">
                  {{ scope.row.missingReason }}
                </div>
                <div v-else class="missing-reason-empty">未填写缺失原因</div>
              </template>
              <template v-else>
                <div>{{ scope.row.handleOpinion || '-' }}</div>
              </template>
              <div v-if="scope.row.confirmedBy" class="confirm-meta">
                {{ scope.row.confirmedBy }} · {{ formatTime(scope.row.confirmedAt) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="scope">
              <template v-if="detail.status === 'OPEN'">
                <el-button
                  v-if="scope.row.checkStatus === 'PENDING'"
                  link
                  type="primary"
                  size="small"
                  @click="openConfirm(scope.row)"
                >确认</el-button>
                <el-button
                  v-else
                  link
                  type="warning"
                  size="small"
                  @click="openRecheck(scope.row)"
                >重新复核</el-button>
                <el-button link type="info" size="small" @click="openRecords(scope.row)">记录</el-button>
              </template>
              <el-button v-else link type="info" size="small" @click="openRecords(scope.row)">记录</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 批次处理记录 -->
        <div class="batch-records">
          <div class="records-title">批次处理记录</div>
          <el-timeline>
            <el-timeline-item
              v-for="record in detail.records"
              :key="record.id"
              :timestamp="`${formatTime(record.createdAt)} · ${record.operator || '-'}`"
              :type="recordActionType(record.action)"
            >
              <el-tag size="small" :type="recordActionType(record.action)">{{ recordActionText(record.action) }}</el-tag>
              <span class="record-opinion">{{ record.opinion }}</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="detail.records.length === 0" description="暂无批次级记录" :image-size="60" />
        </div>
      </template>
    </el-drawer>

    <!-- 确认差异 -->
    <el-dialog v-model="confirmVisible" :title="`确认差异（${currentItem?.assetCode} · ${currentItem ? diffText(currentItem.diffType) : ''}）`" width="560px" append-to-body>
      <el-alert
        v-if="currentItem && currentItem.diffType !== 'MATCH'"
        :title="currentItem.diffDetail"
        type="warning"
        :closable="false"
        show-icon
        class="confirm-alert"
      />
      <el-alert
        v-else-if="currentItem"
        title="账实一致"
        type="success"
        :closable="false"
        show-icon
        class="confirm-alert"
      />
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmRules" label-width="82px">
        <el-form-item
          v-if="currentItem && currentItem.diffType === 'MISSING'"
          label="缺失原因"
          prop="missingReason"
        >
          <el-input
            v-model="confirmForm.missingReason"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="该在册资产本轮未盘到，请写明缺失原因，如：借出维修未归还、搬离阅览区待核查等"
          />
        </el-form-item>
        <el-form-item
          v-else
          label="处理意见"
          prop="handleOpinion"
        >
          <el-input
            v-model="confirmForm.handleOpinion"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="请填写处理意见，如：现场已找到并归位、报修停用、补录标签等"
          />
        </el-form-item>
        <el-form-item label="操作人" prop="operator">
          <el-input v-model="confirmForm.operator" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleConfirm">确认本项</el-button>
      </template>
    </el-dialog>

    <!-- 重新复核 -->
    <el-dialog v-model="recheckVisible" title="重新复核" width="560px" append-to-body>
      <el-alert
        title="复核后该明细将回到“待核”状态，原确认意见与缺失原因保留在处理记录中，需重新写明后才能完成批次。"
        type="warning"
        :closable="false"
        show-icon
        class="confirm-alert"
      />
      <el-form label-width="82px">
        <el-form-item label="复核说明">
          <el-input v-model="recheckOpinion" type="textarea" :rows="3" maxlength="1000" show-word-limit
            placeholder="可填写复核原因（默认“管理员发起重新复核”）" />
        </el-form-item>
        <el-form-item label="操作人" required>
          <el-input v-model="recheckOperator" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recheckVisible = false">取消</el-button>
        <el-button type="warning" :loading="actionLoading" @click="handleRecheck">发起复核</el-button>
      </template>
    </el-dialog>

    <!-- 单项处理记录 -->
    <el-dialog v-model="recordsVisible" title="逐项处理记录" width="560px" append-to-body>
      <el-timeline v-if="currentItem">
        <el-timeline-item
          v-for="record in currentItem.records"
          :key="record.id"
          :timestamp="`${formatTime(record.createdAt)} · ${record.operator || '-'}`"
          :type="recordActionType(record.action)"
        >
          <el-tag size="small" :type="recordActionType(record.action)">{{ recordActionText(record.action) }}</el-tag>
          <span class="record-opinion">{{ record.opinion }}</span>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="currentItem && currentItem.records.length === 0" description="暂无处理记录" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { stocktakeApi, readingAreaApi, tagApi } from '../api'
import { parseStocktakeText } from '../utils/parseStocktakeImport'

const batches = ref([])
const readingAreas = ref([])
const activeTags = ref([])

const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const createForm = reactive({ areaId: null, operator: '', remark: '' })
const createRules = {
  areaId: [{ required: true, message: '请选择盘点分区', trigger: 'change' }],
  operator: [{ required: true, message: '请填写操作人', trigger: 'blur' }]
}

const detailVisible = ref(false)
const detail = ref(null)
const submitLoading = ref(false)
const completeLoading = ref(false)
const actionLoading = ref(false)

const entryMode = ref('manual')
const manualLines = ref([])
const importText = ref('')
const submitRemark = ref('')
const submitOperator = ref('')
const parsedPreview = reactive({ lines: [], errors: [] })

const confirmVisible = ref(false)
const recheckVisible = ref(false)
const recordsVisible = ref(false)
const currentItem = ref(null)
const confirmFormRef = ref()
const confirmForm = reactive({ handleOpinion: '', missingReason: '', operator: '' })
const confirmRules = computed(() => {
  const rules = {
    operator: [{ required: true, message: '请填写操作人', trigger: 'blur' }]
  }
  if (currentItem.value && currentItem.value.diffType === 'MISSING') {
    rules.missingReason = [{ required: true, message: '请填写缺失原因', trigger: 'blur' }]
  } else {
    rules.handleOpinion = [{ required: true, message: '请填写处理意见', trigger: 'blur' }]
  }
  return rules
})
const recheckOpinion = ref('')
const recheckOperator = ref('')

const areaOptions = computed(() =>
  readingAreas.value.filter(area => area.status === 1 || area.status === undefined)
)

// 盘点处理页可按缺失筛出在册未盘到的行
const itemFilter = ref('ALL')

const missingItems = computed(() =>
  detail.value ? detail.value.items.filter(i => i.diffType === 'MISSING') : []
)

// 在册未盘到且还没写明缺失原因的行（已核/待核都算，完成前必须补全）
const missingWithoutReasonItems = computed(() =>
  missingItems.value.filter(i => !i.missingReason || !String(i.missingReason).trim())
)

const filteredItems = computed(() => {
  if (!detail.value) return []
  if (itemFilter.value === 'MISSING') return missingItems.value
  if (itemFilter.value === 'MISSING_NO_REASON') return missingWithoutReasonItems.value
  return detail.value.items
})

const pendingItems = computed(() =>
  detail.value ? detail.value.items.filter(i => i.checkStatus !== 'CONFIRMED') : []
)

const loadBatches = async () => {
  batches.value = await stocktakeApi.listBatches()
}

const loadAreas = async () => {
  readingAreas.value = await readingAreaApi.getAll()
}

const loadTags = async () => {
  const all = await tagApi.getAll()
  activeTags.value = all.filter(tag => tag.status === 1 || tag.status === undefined)
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
    const batch = await stocktakeApi.createBatch({ ...createForm })
    ElMessage.success(`盘点批次 ${batch.batchNo} 已创建`)
    createVisible.value = false
    await loadBatches()
    await openDetail(batch.id)
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    createLoading.value = false
  }
}

const addManualLine = () => {
  manualLines.value.push({ assetCode: '', actualAreaId: null, actualStatus: null, actualTagIds: null })
}

const removeManualLine = index => {
  manualLines.value.splice(index, 1)
}

const resetEntry = () => {
  entryMode.value = 'manual'
  manualLines.value = [{ assetCode: '', actualAreaId: null, actualStatus: null, actualTagIds: null }]
  importText.value = ''
  submitRemark.value = ''
  submitOperator.value = detail.value?.operator || ''
  parsedPreview.lines = []
  parsedPreview.errors = []
  itemFilter.value = 'ALL'
}

const openDetail = async id => {
  try {
    detail.value = await stocktakeApi.getBatch(id)
    resetEntry()
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载盘点详情失败')
  }
}

const refreshDetail = async () => {
  detail.value = await stocktakeApi.getBatch(detail.value.id)
}

const previewImport = () => {
  const result = parseStocktakeText(importText.value, {
    areas: readingAreas.value,
    tags: activeTags.value
  })
  parsedPreview.lines = result.lines
  parsedPreview.errors = result.errors
  if (result.errors.length > 0) {
    ElMessage.error(`解析存在 ${result.errors.length} 个问题，请修正后提交`)
  } else if (result.lines.length > 0) {
    ElMessage.success(`解析成功，共 ${result.lines.length} 行`)
  }
}

const buildSubmitLines = () => {
  if (entryMode.value === 'import') {
    const result = parseStocktakeText(importText.value, {
      areas: readingAreas.value,
      tags: activeTags.value
    })
    parsedPreview.lines = result.lines
    parsedPreview.errors = result.errors
    if (result.lines.length === 0) {
      ElMessage.error('请先粘贴实盘数据')
      return null
    }
    if (result.errors.length > 0) {
      ElMessage.error(`导入数据存在 ${result.errors.length} 个问题，请修正`)
      return null
    }
    return result.lines
  }

  const lines = []
  const seen = new Set()
  for (const row of manualLines.value) {
    const code = (row.assetCode || '').trim()
    if (!code) {
      ElMessage.error('存在未填写资产编号的行')
      return null
    }
    if (seen.has(code)) {
      ElMessage.error(`资产编号重复：${code}`)
      return null
    }
    seen.add(code)
    lines.push({
      assetCode: code,
      actualAreaId: row.actualAreaId || null,
      actualStatus: row.actualStatus === '' || row.actualStatus === undefined ? null : row.actualStatus,
      actualTagIds: row.actualTagIds === null || row.actualTagIds === undefined
        ? null : [...row.actualTagIds]
    })
  }
  return lines
}

const handleSubmit = async () => {
  const lines = buildSubmitLines()
  if (!lines) return
  try {
    await ElMessageBox.confirm(
      `本次提交 ${lines.length} 件实盘资产，系统将重新比对并生成差异${detail.value.items.length ? '，替换当前明细与逐项确认（批次记录保留）' : ''}，是否继续？`,
      '提交实盘',
      { type: 'warning', confirmButtonText: '提交比对', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  submitLoading.value = true
  try {
    if (!submitOperator.value.trim()) {
      ElMessage.error('请填写操作人')
      return
    }
    detail.value = await stocktakeApi.submitActuals(detail.value.id, {
      lines,
      operator: submitOperator.value,
      remark: submitRemark.value
    })
    ElMessage.success(`比对完成：应盘 ${detail.value.expectedCount}，实盘 ${detail.value.actualCount}，差异 ${detail.value.diffCount} 项`)
    resetEntry()
    await loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}

const formatAssetCodeList = items => {
  const codes = items.map(i => i.assetCode)
  const shown = codes.slice(0, 10).join('、')
  return codes.length > 10 ? `${shown} 等 ${codes.length} 件` : shown
}

// 完成前拦截：还有待核行，或在册未盘到的行没写明缺失原因，都要点名提示
const promptIncomplete = () => {
  if (pendingItems.value.length > 0) {
    itemFilter.value = 'ALL'
    ElMessage.error(`还有 ${pendingItems.value.length} 条明细待核，请逐项确认：${formatAssetCodeList(pendingItems.value)}`)
    return true
  }
  if (missingWithoutReasonItems.value.length > 0) {
    itemFilter.value = 'MISSING_NO_REASON'
    ElMessage.error(
      `以下 ${missingWithoutReasonItems.value.length} 件在册资产未盘到且未填写缺失原因，请补全后再完成：${
        formatAssetCodeList(missingWithoutReasonItems.value)}`
    )
    return true
  }
  return false
}

const promptMissingReasonBlock = () => {
  itemFilter.value = 'MISSING_NO_REASON'
  ElMessage.error(
    `以下 ${missingWithoutReasonItems.value.length} 件在册资产未盘到，需先写明缺失原因并确认：${
      formatAssetCodeList(missingWithoutReasonItems.value)}`
  )
}

const handleComplete = async () => {
  if (promptIncomplete()) return
  try {
    await ElMessageBox.confirm(
      `全部 ${detail.value.items.length} 条明细均已确认且缺失原因已写明，完成后批次将锁定，不可再录入或修改。确认完成？`,
      '完成盘点批次',
      { type: 'warning', confirmButtonText: '完成批次', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  completeLoading.value = true
  try {
    detail.value = await stocktakeApi.completeBatch(detail.value.id, {
      handleOpinion: '',
      operator: detail.value.operator
    })
    ElMessage.success('盘点批次已完成并闭环')
    await loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '完成失败')
  } finally {
    completeLoading.value = false
  }
}

const openConfirm = item => {
  currentItem.value = item
  confirmForm.handleOpinion = ''
  // 缺失项确认时填写缺失原因（重新复核后原原因已清空）
  confirmForm.missingReason = item.missingReason || ''
  confirmForm.operator = detail.value.operator || ''
  confirmVisible.value = true
}

const handleConfirm = async () => {
  try {
    await confirmFormRef.value.validate()
  } catch (e) {
    return
  }
  actionLoading.value = true
  try {
    const payload = { ...confirmForm }
    if (currentItem.value.diffType !== 'MISSING') {
      payload.missingReason = ''
    }
    await stocktakeApi.confirmItem(detail.value.id, currentItem.value.id, payload)
    ElMessage.success('已确认')
    confirmVisible.value = false
    await refreshDetail()
    await loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '确认失败')
  } finally {
    actionLoading.value = false
  }
}

const openRecheck = item => {
  currentItem.value = item
  recheckOpinion.value = ''
  recheckOperator.value = detail.value.operator || ''
  recheckVisible.value = true
}

const handleRecheck = async () => {
  if (!recheckOperator.value.trim()) {
    ElMessage.error('请填写操作人')
    return
  }
  actionLoading.value = true
  try {
    await stocktakeApi.recheckItem(detail.value.id, currentItem.value.id, {
      handleOpinion: recheckOpinion.value,
      operator: recheckOperator.value
    })
    ElMessage.success('已退回待核，请重新确认')
    recheckVisible.value = false
    await refreshDetail()
    await loadBatches()
  } catch (e) {
    ElMessage.error(e.message || '复核失败')
  } finally {
    actionLoading.value = false
  }
}

const openRecords = async item => {
  // 抽屉内数据可能已刷新，按 id 取最新记录
  const fresh = detail.value.items.find(i => i.id === item.id) || item
  currentItem.value = fresh
  recordsVisible.value = true
}

const diffTagType = type => ({
  MATCH: 'success',
  MISSING: 'danger',
  SURPLUS: 'danger',
  WRONG_AREA: 'warning',
  STATUS_MISMATCH: 'warning',
  TAG_MISMATCH: 'info'
}[type] || 'info')

const diffText = type => ({
  MATCH: '一致',
  MISSING: '缺失',
  SURPLUS: '盘盈',
  WRONG_AREA: '错区',
  STATUS_MISMATCH: '停用不符',
  TAG_MISMATCH: '标签不符'
}[type] || type)

const statusText = status => (Number(status) === 1 ? '启用' : '停用')

const recordActionType = action => ({
  SUBMIT: 'primary',
  CONFIRM: 'success',
  RECHECK: 'warning',
  COMPLETE: 'success'
}[action] || 'info')

const recordActionText = action => ({
  SUBMIT: '录入实盘',
  CONFIRM: '逐项确认',
  RECHECK: '重新复核',
  COMPLETE: '批次完成'
}[action] || action)

const formatTime = time => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadAreas()
  loadTags()
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

.batch-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.diff-text {
  color: #f56c6c;
  font-weight: 600;
}

.lock-alert {
  margin-bottom: 16px;
}

.action-bar {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 16px 16px;
  margin-bottom: 16px;
}

.entry-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.add-line-btn {
  margin-top: 10px;
}

.import-entry {
  max-width: 900px;
}

.import-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.import-error {
  margin-top: 6px;
}

.submit-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 14px;
}

.submit-remark {
  width: 280px;
}

.submit-operator {
  width: 140px;
}

.item-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.item-table {
  margin-bottom: 24px;
}

.missing-reason {
  color: #f56c6c;
}

.missing-reason-empty {
  color: #f56c6c;
  font-weight: 600;
}

.recheck-badge {
  display: block;
  font-size: 11px;
  color: #e6a23c;
  margin-top: 2px;
}

.confirm-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.batch-records {
  margin-top: 8px;
}

.records-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.record-opinion {
  margin-left: 8px;
}

.confirm-alert {
  margin-bottom: 14px;
}
</style>
