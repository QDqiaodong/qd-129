<template>
  <div class="page-container">
    <div class="page-header">
      <h2>自定义标签管理</h2>
      <el-button type="primary" @click="openAddModal">
        <el-icon><Plus /></el-icon>
        添加标签
      </el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="6" v-for="tag in tags" :key="tag.id">
        <el-card class="tag-card" shadow="hover">
          <div class="tag-header">
            <span class="tag-color" :style="{ background: tag.tagColor }"></span>
            <span class="tag-name">{{ tag.tagName }}</span>
          </div>
          <div class="tag-code">{{ tag.tagCode }}</div>
          <p>{{ tag.description || '暂无描述' }}</p>
          <div class="stats">
            <span>关联桌椅: {{ tag.deskChairCount || 0 }}</span>
          </div>
          <div class="card-footer">
            <el-button size="small" @click="openEditModal(tag)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(tag.id)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog :visible.sync="dialogVisible" :title="isEdit ? '编辑标签' : '添加标签'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标签编码">
          <el-input v-model="form.tagCode" placeholder="请输入标签编码" />
        </el-form-item>
        <el-form-item label="标签名称">
          <el-input v-model="form.tagName" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="标签颜色">
          <el-color-picker v-model="form.tagColor" show-text />
        </el-form-item>
        <el-form-item label="标签描述">
          <el-input v-model="form.description" type="textarea" placeholder="请输入标签描述" />
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
import { tagApi } from '../api'

const tags = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  id: null,
  tagCode: '',
  tagName: '',
  tagColor: '#409EFF',
  description: ''
})

const loadData = async () => {
  tags.value = await tagApi.getAll()
}

const openAddModal = () => {
  isEdit.value = false
  form.value = { id: null, tagCode: '', tagName: '', tagColor: '#409EFF', description: '' }
  dialogVisible.value = true
}

const openEditModal = (tag) => {
  isEdit.value = true
  form.value = { ...tag }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (isEdit.value) {
    await tagApi.update(form.value)
  } else {
    await tagApi.create(form.value)
  }
  dialogVisible.value = false
  await loadData()
  ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确定要删除该标签吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await tagApi.delete(id)
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

.tag-card {
  margin-bottom: 20px;
  transition: transform 0.2s;
}

.tag-card:hover {
  transform: translateY(-4px);
}

.tag-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.tag-color {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  margin-right: 8px;
}

.tag-name {
  font-size: 18px;
  font-weight: 600;
}

.tag-code {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.tag-card p {
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