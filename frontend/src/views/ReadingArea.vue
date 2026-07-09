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
        <el-card class="area-card" shadow="hover">
          <div class="card-header">
            <span class="area-code">{{ area.areaCode }}</span>
            <span class="area-name">{{ area.areaName }}</span>
          </div>
          <div class="card-body">
            <p>{{ area.description || '暂无描述' }}</p>
            <div class="stats">
              <span>桌椅数量: {{ area.deskChairCount || 0 }}</span>
            </div>
          </div>
          <div class="card-footer">
            <el-button size="small" @click="openEditModal(area)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(area.id)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog :visible.sync="dialogVisible" :title="isEdit ? '编辑分区' : '添加分区'" width="500px">
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { readingAreaApi } from '../api'

const readingAreas = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  id: null,
  areaCode: '',
  areaName: '',
  description: ''
})

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
  if (isEdit.value) {
    await readingAreaApi.update(form.value)
  } else {
    await readingAreaApi.create(form.value)
  }
  dialogVisible.value = false
  await loadData()
  ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确定要删除该分区吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await readingAreaApi.delete(id)
  await loadData()
  ElMessage.success('删除成功')
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