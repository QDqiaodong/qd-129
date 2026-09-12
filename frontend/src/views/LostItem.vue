<template>
  <div class="page-container">
    <div class="page-header">
      <h2>遗失物品登记</h2>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">登记遗失物品</el-button>
    </div>

    <!-- 列表筛选：按分区和是否待领 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="阅览分区">
          <el-select v-model="filters.areaId" placeholder="全部分区" clearable style="width: 200px" @change="loadItems">
            <el-option v-for="area in readingAreas" :key="area.id" :label="area.areaName" :value="area.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否待领">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px" @change="loadItems">
            <el-option label="待领取" value="PENDING" />
            <el-option label="已领取" value="CLAIMED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilter">重置</el-button>
          <el-button @click="loadItems">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card">
      <el-table :data="items" border>
        <el-table-column prop="itemNo" label="遗失单号" width="190" />
        <el-table-column prop="itemName" label="物品名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="所属分区" min-width="130">
          <template #default="scope">{{ scope.row.areaName }}（{{ scope.row.areaCode }}）</template>
        </el-table-column>
        <el-table-column prop="assetCode" label="对应桌椅" width="100" />
        <el-table-column prop="storageLocation" label="暂存位置" min-width="130" show-overflow-tooltip />
        <el-table-column label="登记" width="170">
          <template #default="scope">
            <div>{{ scope.row.foundBy }}</div>
            <div class="meta-time">{{ formatTime(scope.row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="是否待领" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'PENDING' ? 'warning' : 'success'" size="small">
              {{ scope.row.status === 'PENDING' ? '待领取' : '已领取' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="领取信息" min-width="200">
          <template #default="scope">
            <div v-if="scope.row.status === 'CLAIMED'">
              <div>{{ scope.row.claimerName }} · {{ scope.row.claimedBy }}经办</div>
              <div class="meta-time">{{ formatTime(scope.row.claimedAt) }}</div>
              <div class="meta-conclusion">{{ scope.row.claimConclusion }}</div>
            </div>
            <span v-else class="readonly-text">待领取，暂存于 {{ scope.row.storageLocation }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'PENDING'"
              size="small"
              link
              type="primary"
              @click="openClaimDialog(scope.row)"
            >领取登记</el-button>
            <span v-else class="readonly-text">已闭环</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="items.length === 0" description="暂无遗失物品记录" />
    </el-card>

    <!-- 登记遗失物品 -->
    <el-dialog v-model="createVisible" title="登记遗失物品" width="560px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="所属分区" prop="areaId">
          <el-select v-model="createForm.areaId" placeholder="请选择阅览分区" style="width: 100%" @change="onCreateAreaChange">
            <el-option
              v-for="area in areaOptions"
              :key="area.id"
              :label="`${area.areaName}（${area.areaCode}）`"
              :value="area.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="对应桌椅" prop="deskChairId">
          <el-select
            v-model="createForm.deskChairId"
            placeholder="请选择捡到位置对应的桌椅"
            style="width: 100%"
            filterable
            :disabled="!createForm.areaId"
          >
            <el-option
              v-for="desk in createDeskOptions"
              :key="desk.id"
              :label="`${desk.assetCode}（${Number(desk.status) === 1 ? '可用' : '停用'}）`"
              :value="desk.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物品名称" prop="itemName">
          <el-input v-model="createForm.itemName" maxlength="200" placeholder="如 黑色双肩包" />
        </el-form-item>
        <el-form-item label="暂存位置" prop="storageLocation">
          <el-input v-model="createForm.storageLocation" maxlength="200" placeholder="如 服务台抽屉 3 号" />
        </el-form-item>
        <el-form-item label="登记值班员" prop="operator">
          <el-input v-model="createForm.operator" maxlength="100" placeholder="请填写登记值班员" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">登记</el-button>
      </template>
    </el-dialog>

    <!-- 领取登记：必须核验领取人并填写领取结论 -->
    <el-dialog v-model="claimVisible" title="领取登记" width="560px">
      <template v-if="claimingItem">
        <el-descriptions :column="2" border size="small" class="claim-desc">
          <el-descriptions-item label="遗失单号">{{ claimingItem.itemNo }}</el-descriptions-item>
          <el-descriptions-item label="物品名称">{{ claimingItem.itemName }}</el-descriptions-item>
          <el-descriptions-item label="对应桌椅">{{ claimingItem.assetCode }}</el-descriptions-item>
          <el-descriptions-item label="暂存位置">{{ claimingItem.storageLocation }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="领取必须核验领取人身份并填写领取结论；闭环后该桌椅方可再开高峰占座。"
          class="claim-alert"
        />
        <el-form ref="claimFormRef" :model="claimForm" :rules="claimRules" label-width="92px">
          <el-form-item label="领取人" prop="claimerName">
            <el-input v-model="claimForm.claimerName" maxlength="100" placeholder="请填写领取人姓名" />
          </el-form-item>
          <el-form-item label="核验信息" prop="claimerVerify">
            <el-input
              v-model="claimForm.claimerVerify"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="证件号/学工号及核对结果，如 学生证 20230101，核对包内校园卡一致"
            />
          </el-form-item>
          <el-form-item label="领取结论" prop="claimConclusion">
            <el-input
              v-model="claimForm.claimConclusion"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="如 核验通过，物品完好交还领取人"
            />
          </el-form-item>
          <el-form-item label="经办值班员" prop="operator">
            <el-input v-model="claimForm.operator" maxlength="100" placeholder="请填写经办值班员" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="claimVisible = false">取消</el-button>
        <el-button type="primary" :loading="claimSubmitting" @click="submitClaim">确认领取闭环</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { lostItemApi, readingAreaApi, deskChairApi } from '../api'

const items = ref([])
const readingAreas = ref([])
const allDesks = ref([])
const filters = reactive({ areaId: null, status: null })

// —— 登记 ——
const createVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref()
const createForm = reactive({ areaId: null, deskChairId: null, itemName: '', storageLocation: '', operator: '', remark: '' })
const createRules = {
  areaId: [{ required: true, message: '请选择所属分区', trigger: 'change' }],
  deskChairId: [{ required: true, message: '请选择对应桌椅', trigger: 'change' }],
  itemName: [{ required: true, message: '请填写物品名称', trigger: 'blur' }],
  storageLocation: [{ required: true, message: '请填写暂存位置', trigger: 'blur' }],
  operator: [{ required: true, message: '请填写登记值班员', trigger: 'blur' }]
}

// —— 领取 ——
const claimVisible = ref(false)
const claimSubmitting = ref(false)
const claimingItem = ref(null)
const claimFormRef = ref()
const claimForm = reactive({ claimerName: '', claimerVerify: '', claimConclusion: '', operator: '' })
const claimRules = {
  claimerName: [{ required: true, message: '请填写领取人', trigger: 'blur' }],
  claimerVerify: [{ required: true, message: '请填写领取人核验信息', trigger: 'blur' }],
  claimConclusion: [{ required: true, message: '请填写领取结论', trigger: 'blur' }],
  operator: [{ required: true, message: '请填写经办值班员', trigger: 'blur' }]
}

const areaOptions = computed(() =>
  readingAreas.value.filter(area => area.status === 1 || area.status === undefined)
)

const createDeskOptions = computed(() =>
  allDesks.value.filter(desk => desk.areaId === createForm.areaId)
)

const loadItems = async () => {
  items.value = await lostItemApi.list({ areaId: filters.areaId, status: filters.status })
}

const loadAreas = async () => {
  readingAreas.value = await readingAreaApi.getAll()
}

const resetFilter = () => {
  filters.areaId = null
  filters.status = null
  loadItems()
}

const openCreateDialog = async () => {
  createForm.areaId = null
  createForm.deskChairId = null
  createForm.itemName = ''
  createForm.storageLocation = ''
  createForm.operator = ''
  createForm.remark = ''
  createVisible.value = true
  // 含停用桌椅：停用桌旁也可能捡到物品
  allDesks.value = await deskChairApi.getAll({ params: { includeDisabled: true } })
}

const onCreateAreaChange = () => {
  createForm.deskChairId = null
}

const submitCreate = async () => {
  try {
    await createFormRef.value.validate()
  } catch (e) {
    return
  }
  createSubmitting.value = true
  try {
    const item = await lostItemApi.create({
      areaId: createForm.areaId,
      deskChairId: createForm.deskChairId,
      itemName: createForm.itemName,
      storageLocation: createForm.storageLocation,
      remark: createForm.remark,
      operator: createForm.operator
    })
    ElMessage.success(`遗失单 ${item.itemNo} 已登记，待领取；该桌椅领取闭环前不能开高峰占座`)
    createVisible.value = false
    await loadItems()
  } catch (e) {
    ElMessage.error(e.message || '登记失败')
  } finally {
    createSubmitting.value = false
  }
}

const openClaimDialog = item => {
  claimingItem.value = item
  claimForm.claimerName = ''
  claimForm.claimerVerify = ''
  claimForm.claimConclusion = ''
  claimForm.operator = ''
  claimVisible.value = true
}

const submitClaim = async () => {
  try {
    await claimFormRef.value.validate()
  } catch (e) {
    return
  }
  claimSubmitting.value = true
  try {
    await lostItemApi.claim(claimingItem.value.id, {
      claimerName: claimForm.claimerName,
      claimerVerify: claimForm.claimerVerify,
      claimConclusion: claimForm.claimConclusion,
      operator: claimForm.operator
    })
    ElMessage.success(`遗失单 ${claimingItem.value.itemNo} 已领取闭环`)
    claimVisible.value = false
    await loadItems()
  } catch (e) {
    ElMessage.error(e.message || '领取登记失败')
  } finally {
    claimSubmitting.value = false
  }
}

const formatTime = time => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadAreas()
  loadItems()
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
.list-card {
  margin-bottom: 20px;
}

.meta-time {
  color: #909399;
  font-size: 12px;
}

.meta-conclusion {
  color: #67c23a;
  font-size: 12px;
}

.readonly-text {
  color: #909399;
  font-size: 12px;
}

.claim-desc {
  margin-bottom: 12px;
}

.claim-alert {
  margin-bottom: 16px;
}
</style>
