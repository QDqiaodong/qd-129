<template>
  <div class="page-container">
    <div class="page-header">
      <h2>分区变更记录</h2>
    </div>

    <el-table :data="changeLogs" border>
      <el-table-column prop="assetCode" label="资产编号" />
      <el-table-column prop="oldAreaName" label="原分区">
        <template #default="scope">
          {{ scope.row.oldAreaName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="newAreaName" label="新分区" />
      <el-table-column prop="changeReason" label="变更原因" />
      <el-table-column prop="operator" label="操作人" />
      <el-table-column prop="createdAt" label="变更时间">
        <template #default="scope">
          {{ formatTime(scope.row.createdAt) }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="changeLogs.length === 0" description="暂无变更记录" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { changeLogApi } from '../api'

const changeLogs = ref([])

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const loadData = async () => {
  changeLogs.value = await changeLogApi.getAll()
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
</style>