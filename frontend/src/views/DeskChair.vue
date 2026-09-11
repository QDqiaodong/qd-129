<template>
  <div class="page-container">
    <div class="page-header">
      <h2>阅览桌椅管理</h2>
      <el-button type="primary" @click="openAddModal">
        <el-icon><Plus /></el-icon>
        添加桌椅
      </el-button>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="阅览分区">
          <el-select v-model="filters.areaId" placeholder="请选择分区" clearable>
            <el-option v-for="area in readingAreas" :key="area.id" :label="area.areaName" :value="area.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签筛选">
          <el-select v-model="filters.tagIds" placeholder="请选择标签" multiple>
            <el-option v-for="tag in tags" :key="tag.id" :label="tag.tagName" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="deskChairs" border>
      <el-table-column prop="assetCode" label="资产编号" />
      <el-table-column prop="capacity" label="容纳人数" />
      <el-table-column prop="areaName" label="所属分区" />
      <el-table-column prop="dimensions" label="尺寸" />
      <el-table-column label="标签" min-width="200">
        <template #default="scope">
          <el-tag
            v-for="tag in scope.row.tags"
            :key="tag.id"
            :style="{ background: tag.tagColor }"
            size="small"
          >
            {{ tag.tagName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="scope">
          <el-button size="small" @click="openEditModal(scope.row)">编辑</el-button>
          <el-button size="small" type="warning" @click="openTagModal(scope.row)">编辑标签</el-button>
          <el-button size="small" type="info" @click="openUpdateAreaModal(scope.row)">调整分区</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑桌椅' : '添加桌椅'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="资产编号">
          <el-input v-model="form.assetCode" placeholder="请输入资产编号" />
        </el-form-item>
        <el-form-item label="容纳人数">
          <el-input-number v-model="form.capacity" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="所属分区">
          <el-select v-model="form.areaId" placeholder="请选择分区">
            <el-option v-for="area in readingAreas" :key="area.id" :label="area.areaName" :value="area.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="尺寸规格">
          <el-input v-model="form.dimensions" placeholder="请输入尺寸规格" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tagDialogVisible" title="编辑标签" width="500px">
      <div class="tag-list">
        <div class="tag-item" v-for="tag in tags" :key="tag.id">
          <el-checkbox
            :checked="selectedTagIds.includes(tag.id)"
            @change="toggleTag(tag.id)"
          >
            <span class="tag-color" :style="{ background: tag.tagColor }"></span>
            {{ tag.tagName }}
          </el-checkbox>
        </div>
      </div>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTags">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="areaDialogVisible" title="调整分区" width="500px">
      <el-form :model="areaForm" label-width="100px">
        <el-form-item label="当前分区">
          <el-input :value="currentAreaName" disabled />
        </el-form-item>
        <el-form-item label="新分区">
          <el-select v-model="areaForm.newAreaId" placeholder="请选择新分区">
            <el-option v-for="area in readingAreas" :key="area.id" :label="area.areaName" :value="area.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="areaForm.changeReason" type="textarea" placeholder="请输入变更原因" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="areaForm.operator" placeholder="请输入操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="areaDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateArea">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deskChairApi, readingAreaApi, tagApi } from '../api'

const deskChairs = ref([])
const readingAreas = ref([])
const tags = ref([])
const dialogVisible = ref(false)
const tagDialogVisible = ref(false)
const areaDialogVisible = ref(false)
const isEdit = ref(false)
const currentDeskChair = ref(null)
const selectedTagIds = ref([])

const filters = ref({
  areaId: null,
  tagIds: []
})

const form = ref({
  id: null,
  assetCode: '',
  capacity: 1,
  areaId: null,
  dimensions: ''
})

const areaForm = ref({
  newAreaId: null,
  changeReason: '',
  operator: 'admin'
})

const currentAreaName = computed(() => {
  if (!currentDeskChair.value) return ''
  const area = readingAreas.value.find(a => a.id === currentDeskChair.value.areaId)
  return area ? area.areaName : ''
})

const loadData = async () => {
  deskChairs.value = await deskChairApi.getAll()
}

const loadAreas = async () => {
  readingAreas.value = await readingAreaApi.getAll()
}

const loadTags = async () => {
  tags.value = await tagApi.getAll()
}

const handleFilter = async () => {
  if (filters.value.areaId) {
    deskChairs.value = await deskChairApi.getByAreaId(filters.value.areaId)
  } else if (filters.value.tagIds && filters.value.tagIds.length > 0) {
    deskChairs.value = await deskChairApi.getByTagIds(filters.value.tagIds)
  } else {
    await loadData()
  }
}

const resetFilter = () => {
  filters.value = { areaId: null, tagIds: [] }
  loadData()
}

const openAddModal = () => {
  isEdit.value = false
  form.value = { id: null, assetCode: '', capacity: 1, areaId: null, dimensions: '' }
  dialogVisible.value = true
}

const openEditModal = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await deskChairApi.update(form.value)
    } else {
      await deskChairApi.create(form.value)
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
    await ElMessageBox.confirm('确定要删除该桌椅吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deskChairApi.delete(id)
    await loadData()
    ElMessage.success('删除成功')
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

const openTagModal = (row) => {
  currentDeskChair.value = row
  selectedTagIds.value = row.tags ? row.tags.map(t => t.id) : []
  tagDialogVisible.value = true
}

const toggleTag = (tagId) => {
  const index = selectedTagIds.value.indexOf(tagId)
  if (index > -1) {
    selectedTagIds.value.splice(index, 1)
  } else {
    selectedTagIds.value.push(tagId)
  }
}

const handleSaveTags = async () => {
  try {
    await deskChairApi.bindTags(currentDeskChair.value.id, selectedTagIds.value)
    tagDialogVisible.value = false
    await loadData()
    ElMessage.success('标签更新成功')
  } catch (e) {
    ElMessage.error(e.message || '标签更新失败')
  }
}

const openUpdateAreaModal = (row) => {
  currentDeskChair.value = row
  areaForm.value = { newAreaId: null, changeReason: '', operator: 'admin' }
  areaDialogVisible.value = true
}

const handleUpdateArea = async () => {
  try {
    await deskChairApi.updateArea(currentDeskChair.value.id, {
      newAreaId: areaForm.value.newAreaId,
      changeReason: areaForm.value.changeReason,
      operator: areaForm.value.operator
    })
    areaDialogVisible.value = false
    await loadData()
    ElMessage.success('分区调整成功')
  } catch (e) {
    ElMessage.error(e.message || '分区调整失败')
  }
}

onMounted(() => {
  loadData()
  loadAreas()
  loadTags()
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

.filter-card {
  margin-bottom: 20px;
}

.tag-list {
  max-height: 300px;
  overflow-y: auto;
}

.tag-item {
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.tag-item :deep(.el-checkbox) {
  flex: 1;
}

.tag-color {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  display: inline-block;
  margin-right: 8px;
}
</style>