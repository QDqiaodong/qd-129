<template>
  <div class="page-container">
    <div class="page-header">
      <h2>阅览分区管理</h2>
      <el-button type="primary" @click="openAddModal">
        <el-icon><Plus /></el-icon>
        添加分区
      </el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="8" v-for="area in readingAreas" :key="area.id">
        <el-card class="area-card" :class="{ 'area-card-closed': isAreaClosed(area) }" shadow="hover">
          <div class="card-header">
            <span class="area-code">{{ area.areaCode }}</span>
            <span class="area-name">{{ area.areaName }}</span>
          </div>
          <div class="card-body">
            <p>{{ area.description || '暂无描述' }}</p>
            <div class="stats">
              <span>桌椅数量: {{ area.deskChairCount || 0 }}</span>
            </div>
            <!-- 今日闭馆牌：与启用/停用相互独立，到期自动失效；进行中批次不受影响，只拦截新开批次 -->
            <div v-if="isAreaClosed(area)" class="closed-panel">
              <div class="closed-line">
                <el-tag type="danger" size="small" effect="dark">今日闭馆</el-tag>
                <span class="closed-until">闭馆至 {{ formatClosedUntil(area.closedUntil) }}</span>
              </div>
              <el-button size="small" type="warning" plain :loading="closingId === area.id" @click="handleClearClosed(area)">
                提前摘牌恢复
              </el-button>
            </div>
            <div v-else-if="area.closedUntil" class="expired-line">
              闭馆已于 {{ formatClosedUntil(area.closedUntil) }} 到期，自动恢复开放
            </div>
            <el-button
              v-else
              size="small"
              type="danger"
              plain
              class="close-btn"
              @click="openCloseDialog(area)"
            >挂今日闭馆</el-button>
          </div>
          <div class="card-footer">
            <el-button size="small" @click="openEditModal(area)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(area.id)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑分区' : '添加分区'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="分区编码">
          <el-input v-model="form.areaCode" placeholder="请输入分区编码" />
        </el-form-item>
        <el-form-item label="分区名称">
          <el-input v-model="form.areaName" placeholder="请输入分区名称" />
        </el-form-item>
        <el-form-item label="分区描述">
          <el-input v-model="form.description" type="textarea" placeholder="请输入分区描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 挂今日闭馆：只登记结束时刻，不改动分区启用状态与进行中的占座批次 -->
    <el-dialog v-model="closeVisible" title="挂“今日闭馆”牌" width="460px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="闭馆期间高峰占座不能在该分区开新批次；到期自动解除，也可随时提前摘牌。已在进行的占座批次不受影响。"
        class="close-tip"
      />
      <el-form label-width="110px">
        <el-form-item label="阅览分区">
          <span>{{ closeForm.areaName }}（{{ closeForm.areaCode }}）</span>
        </el-form-item>
        <el-form-item label="闭馆结束时刻" required>
          <el-date-picker
            v-model="closeForm.closedUntil"
            type="datetime"
            placeholder="请选择闭馆结束时刻"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled-date="disablePastDate"
            :clearable="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="danger" :loading="closeLoading" @click="handleMarkClosed">挂上闭馆牌</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { readingAreaApi } from '../api'
import { isAreaClosed, formatClosedUntil } from '../utils/areaClosure'

const readingAreas = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  id: null,
  areaCode: '',
  areaName: '',
  description: ''
})

// —— 今日闭馆 ——
const closeVisible = ref(false)
const closeLoading = ref(false)
const closingId = ref(null)
const closeForm = reactive({ id: null, areaCode: '', areaName: '', closedUntil: '' })

const loadData = async () => {
  readingAreas.value = await readingAreaApi.getAll()
}

const openAddModal = () => {
  isEdit.value = false
  form.value = { id: null, areaCode: '', areaName: '', description: '' }
  dialogVisible.value = true
}

const openEditModal = (area) => {
  isEdit.value = true
  form.value = { ...area }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await readingAreaApi.update(form.value)
    } else {
      await readingAreaApi.create(form.value)
    }
    dialogVisible.value = false
    await loadData()
    ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
  } catch (e) {
    ElMessage.error(e.message || (isEdit.value ? '更新失败' : '添加失败'))
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该分区吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await readingAreaApi.delete(id)
    await loadData()
    ElMessage.success('删除成功')
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

// 默认结束时刻：今天 22:00；若已过 22:00 则顺延到明天同一时间
const defaultClosedUntil = () => {
  const d = new Date()
  d.setSeconds(0, 0)
  d.setHours(22, 0, 0, 0)
  if (d.getTime() <= Date.now()) {
    d.setDate(d.getDate() + 1)
  }
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

const disablePastDate = (date) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

const openCloseDialog = (area) => {
  closeForm.id = area.id
  closeForm.areaCode = area.areaCode
  closeForm.areaName = area.areaName
  closeForm.closedUntil = defaultClosedUntil()
  closeVisible.value = true
}

const handleMarkClosed = async () => {
  if (!closeForm.closedUntil || new Date(closeForm.closedUntil).getTime() <= Date.now()) {
    ElMessage.error('请选择晚于当前时间的闭馆结束时刻')
    return
  }
  closeLoading.value = true
  try {
    await readingAreaApi.markClosed(closeForm.id, closeForm.closedUntil)
    ElMessage.success(`已挂出“今日闭馆”牌，闭馆至 ${formatClosedUntil(closeForm.closedUntil)}`)
    closeVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error(e.message || '挂闭馆失败')
  } finally {
    closeLoading.value = false
  }
}

const handleClearClosed = async (area) => {
  try {
    await ElMessageBox.confirm(
      `确认提前摘除「${area.areaName}」的今日闭馆牌？摘牌后该分区即刻可以开高峰占座批次。`,
      '提前恢复开放',
      { confirmButtonText: '摘牌恢复', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  closingId.value = area.id
  try {
    await readingAreaApi.clearClosed(area.id)
    ElMessage.success('闭馆牌已摘除，分区恢复开放')
    await loadData()
  } catch (e) {
    ElMessage.error(e.message || '摘牌失败')
  } finally {
    closingId.value = null
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-container {
  max-width: 1200px;
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

.area-card {
  margin-bottom: 20px;
  transition: transform 0.2s;
}

.area-card:hover {
  transform: translateY(-4px);
}

.area-card-closed {
  background: #fdf6f6;
  border-color: #f9c7c8;
}

.card-header {
  margin-bottom: 12px;
}

.area-code {
  display: inline-block;
  background: #409eff;
  color: #fff;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  margin-right: 8px;
}

.area-name {
  font-size: 18px;
  font-weight: 600;
}

.card-body p {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 12px;
}

.stats {
  font-size: 13px;
  color: #999;
  margin-bottom: 10px;
}

.closed-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 10px;
  margin-bottom: 8px;
  border-radius: 4px;
  background: #fef0f0;
}

.closed-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.closed-until {
  color: #f56c6c;
  font-size: 12px;
  font-weight: 600;
}

.expired-line {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.close-btn {
  margin-bottom: 8px;
}

.close-tip {
  margin-bottom: 16px;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
</style>
