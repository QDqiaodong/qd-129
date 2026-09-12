<template>
  <div class="app-container">
    <aside class="sidebar">
      <div class="sidebar-header">
        <h1>图书馆桌椅管理</h1>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        router
      >
        <el-menu-item index="/reading-area">
          <el-icon><Location /></el-icon>
          <span>阅览分区</span>
        </el-menu-item>
        <el-menu-item index="/capacity-dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>容量运营看板</span>
        </el-menu-item>
        <el-menu-item index="/desk-chair">
          <el-icon><Grid /></el-icon>
          <span>桌椅管理</span>
        </el-menu-item>
        <el-menu-item index="/batch-transfer">
          <el-icon><Switch /></el-icon>
          <span>批量调区</span>
        </el-menu-item>
        <el-menu-item index="/tag">
          <el-icon><PriceTag /></el-icon>
          <span>标签管理</span>
        </el-menu-item>
        <el-menu-item index="/change-log">
          <el-icon><Document /></el-icon>
          <span>变更记录</span>
        </el-menu-item>
        <el-menu-item index="/stocktake">
          <el-icon><Finished /></el-icon>
          <span>桌椅盘点</span>
        </el-menu-item>
        <el-menu-item index="/seat-hold">
          <el-icon><Clock /></el-icon>
          <span>高峰占座</span>
        </el-menu-item>
        <el-menu-item index="/lost-item">
          <el-icon><Box /></el-icon>
          <span>遗失物品</span>
        </el-menu-item>
      </el-menu>
    </aside>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Location, Grid, PriceTag, Document, Switch, DataAnalysis, Finished, Clock, Box } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.app-container {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 250px;
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-header h1 {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.sidebar-menu {
  flex: 1;
  padding-top: 16px;
}

.sidebar-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.85);
  height: 48px;
  line-height: 48px;
  margin: 0 8px;
  border-radius: 8px;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.1);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: #409eff;
  color: #fff;
}

.main-content {
  flex: 1;
  background: #f5f5f5;
  overflow-y: auto;
  padding: 24px;
}
</style>