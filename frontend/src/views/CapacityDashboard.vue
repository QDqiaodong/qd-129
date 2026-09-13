<template>
  <div class="page-container">
    <div class="page-header">
      <h2>阅览区容量运营看板</h2>
      <el-button :loading="statsLoading" @click="loadStats">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <!-- 全馆概览 -->
    <div class="overview-row">
      <el-card shadow="hover" class="overview-card">
        <div class="overview-label">阅览分区</div>
        <div class="overview-value">{{ overview.areaCount }}</div>
        <div class="overview-sub">个分区</div>
      </el-card>
      <el-card shadow="hover" class="overview-card">
        <div class="overview-label">桌椅总数</div>
        <div class="overview-value">{{ overview.totalCount }}</div>
        <div class="overview-sub">
          可用 {{ overview.availableCount }} / 占座占用 {{ overview.occupiedCount }} / 停用 {{ overview.disabledCount }}
        </div>
      </el-card>
      <el-card shadow="hover" class="overview-card">
        <div class="overview-label">可用容纳人数</div>
        <div class="overview-value primary">{{ overview.totalCapacity }}</div>
        <div class="overview-sub">人（仅统计可用桌椅）</div>
      </el-card>
      <!-- 今晚闭馆分区：值班员一眼看到张数；有闭馆时整卡可点，进清单再下钻分区看闭馆到几点 -->
      <el-card
        shadow="hover"
        class="overview-card"
        :class="{ 'overview-card-clickable': closedAreas.length > 0 }"
        @click="closedAreas.length > 0 && (closedListVisible = true)"
      >
        <div class="overview-label">
          今晚闭馆分区
          <el-icon v-if="closedAreas.length > 0" class="overview-arrow"><ArrowRightBold /></el-icon>
        </div>
        <div class="overview-value" :class="{ danger: closedAreas.length > 0 }">{{ closedAreas.length }}</div>
        <div class="overview-sub">
          {{ closedAreas.length > 0 ? '个分区闭馆中 · 点击查看' : '个分区（全部开放）' }}
        </div>
      </el-card>
      <el-card shadow="hover" class="overview-card">
        <div class="overview-label">区间调区变更</div>
        <div class="overview-value warning">{{ overview.trendCount }}</div>
        <div class="overview-sub">{{ dateRange[0] }} ~ {{ dateRange[1] }}</div>
      </el-card>
    </div>

    <!-- 今晚闭馆分区清单：点数字卡弹出，逐区给出闭馆至时刻，再点一行进对应分区下钻页 -->
    <el-dialog v-model="closedListVisible" title="今晚闭馆分区" width="520px" append-to-body>
      <el-alert
        type="error"
        :closable="false"
        show-icon
        title="闭馆结束时刻前，这些分区不能开新批次高峰占座；到期自动恢复开放，本数字归零。"
        class="closed-list-alert"
      />
      <el-empty v-if="closedAreas.length === 0" description="当前没有闭馆中的分区" />
      <ul v-else class="closed-list">
        <li
          v-for="area in closedAreas"
          :key="area.areaId"
          class="closed-list-item"
          @click="openClosedArea(area.areaId)"
        >
          <div class="closed-item-main">
            <span class="area-code">{{ area.areaCode }}</span>
            <span class="closed-item-name">{{ area.areaName }}</span>
          </div>
          <div class="closed-item-aside">
            <el-tag type="danger" size="small" effect="dark">闭馆至 {{ formatClosedUntil(area.closedUntil, now) }}</el-tag>
            <el-icon class="closed-item-arrow"><ArrowRight /></el-icon>
          </div>
        </li>
      </ul>
    </el-dialog>

    <!-- 分区容量卡片 -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">分区容量明细</span>
          <span class="section-tip">点击分区卡片可下钻查看桌椅、待领遗失与最近变更</span>
        </div>
      </template>

      <div v-loading="statsLoading">
        <el-result
          v-if="statsError"
          icon="error"
          title="分区容量数据加载失败"
          :sub-title="statsError"
        >
          <template #extra>
            <el-button type="primary" @click="loadStats">点击重试</el-button>
          </template>
        </el-result>

        <el-empty v-else-if="!statsLoading && areaStats.length === 0" description="暂无分区数据" />

        <el-row v-else :gutter="16">
          <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="area in areaStats" :key="area.areaId">
            <div class="area-card" :class="{ 'area-card-closed': cardClosed(area) }" @click="openDetail(area.areaId)">
              <div class="area-card-header">
                <div class="area-title-wrap">
                  <span class="area-code">{{ area.areaCode }}</span>
                  <span class="area-name">{{ area.areaName }}</span>
                </div>
                <el-tag size="small" :type="area.areaStatus === 1 ? 'success' : 'info'">
                  {{ area.areaStatus === 1 ? '启用' : '停用' }}
                </el-tag>
              </div>

              <!-- 今日闭馆牌：值班员在阅览分区页挂出，结束时刻后自动消失，不与“停用”混为一谈 -->
              <div v-if="isAreaClosed(area)" class="closed-banner">
                <el-icon><CircleCloseFilled /></el-icon>
                <span>今日闭馆，闭馆至 {{ formatClosedUntil(area.closedUntil) }}</span>
              </div>

              <div class="area-metrics">
                <div class="metric">
                  <span class="metric-value">{{ area.totalCount ?? 0 }}</span>
                  <span class="metric-label">桌椅总数</span>
                </div>
                <div class="metric">
                  <span class="metric-value available">{{ area.availableCount ?? 0 }}</span>
                  <span class="metric-label">可用</span>
                </div>
                <div class="metric">
                  <span class="metric-value occupied">{{ area.occupiedCount ?? 0 }}</span>
                  <span class="metric-label">占座占用</span>
                </div>
                <div class="metric">
                  <span class="metric-value disabled">{{ area.disabledCount ?? 0 }}</span>
                  <span class="metric-label">停用</span>
                </div>
                <div class="metric">
                  <span class="metric-value primary">{{ area.totalCapacity ?? 0 }}</span>
                  <span class="metric-label">容纳人数</span>
                </div>
                <div class="metric">
                  <span class="metric-value" :class="{ lost: (area.pendingLostCount ?? 0) > 0 }">
                    {{ area.pendingLostCount ?? 0 }}
                  </span>
                  <span class="metric-label">待领遗失</span>
                </div>
              </div>

              <div class="area-tags">
                <template v-if="area.tagStats && area.tagStats.length">
                  <el-tooltip
                    v-for="tag in area.tagStats"
                    :key="tag.tagId"
                    :content="`${tag.tagName}：${tag.deskChairCount} 件`"
                    placement="top"
                  >
                    <span
                      class="area-tag"
                      :style="{ backgroundColor: tag.tagColor || '#909399' }"
                    >{{ tag.tagName }} · {{ tag.deskChairCount }}</span>
                  </el-tooltip>
                </template>
                <span v-else class="no-tag">暂无标签</span>
              </div>

              <div class="area-card-footer">
                <span>查看桌椅与变更记录</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-card>

    <!-- 调区变更趋势 -->
    <el-card class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">调区变更趋势</span>
          <div class="trend-filters">
            <el-select
              v-model="trendAreaId"
              placeholder="全馆分区"
              clearable
              class="trend-area-select"
              @change="loadTrend"
            >
              <el-option
                v-for="area in areaStats"
                :key="area.areaId"
                :label="area.areaName"
                :value="area.areaId"
              />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              :disabled-date="disableFutureDate"
              @change="loadTrend"
            />
            <el-button :loading="trendLoading" @click="loadTrend">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="trendLoading" class="trend-body">
        <el-result
          v-if="trendError"
          icon="error"
          title="变更趋势加载失败"
          :sub-title="trendError"
        >
          <template #extra>
            <el-button type="primary" @click="loadTrend">点击重试</el-button>
          </template>
        </el-result>

        <template v-else>
          <el-empty v-if="trendData.length === 0" description="所选区间暂无数据" />
          <div v-else-if="trendData.length > 31" class="trend-scroll">
            <div class="trend-chart">
              <bar-item v-for="item in trendData" :key="item.date" :item="item" :max="trendMax" />
            </div>
          </div>
          <div v-else class="trend-chart">
            <bar-item v-for="item in trendData" :key="item.date" :item="item" :max="trendMax" />
          </div>
        </template>
      </div>
    </el-card>

    <!-- 分区下钻抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="60%">
      <div v-loading="detailLoading">
        <el-result
          v-if="detailError"
          icon="error"
          title="分区明细加载失败"
          :sub-title="detailError"
        >
          <template #extra>
            <el-button type="primary" @click="reloadDetail">点击重试</el-button>
          </template>
        </el-result>

        <template v-else-if="detail">
          <el-alert
            v-if="isAreaClosed(detail)"
            type="error"
            :closable="false"
            show-icon
            :title="`该区今日闭馆，闭馆至 ${formatClosedUntil(detail.closedUntil)}；结束时刻前不能开新批次高峰占座。`"
            class="detail-closed-alert"
          />
          <el-descriptions :column="4" border class="detail-desc">
            <el-descriptions-item label="桌椅总数">{{ detail.totalCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="可用">
              <span class="available">{{ detail.availableCount ?? 0 }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="占座占用">
              <span class="occupied">{{ detail.occupiedCount ?? 0 }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="停用">
              <span class="disabled">{{ detail.disabledCount ?? 0 }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="可用容纳人数">
              <span class="primary">{{ detail.totalCapacity ?? 0 }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="占用容纳人数">
              <span class="occupied">{{ detail.occupiedCapacity ?? 0 }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="分区描述" :span="2">
              {{ detail.description || '暂无描述' }}
            </el-descriptions-item>
          </el-descriptions>

          <h3 class="detail-subtitle">待领取遗失物品（{{ detail.pendingLostItems?.length || 0 }}）</h3>
          <el-table
            ref="lostTableRef"
            :data="detail.pendingLostItems || []"
            border
            size="small"
            row-key="id"
            class="lost-table"
          >
            <el-table-column type="expand">
              <template #default="scope">
                <div class="claim-panel">
                  <el-alert
                    v-if="claimErrors[scope.row.id]"
                    type="error"
                    :closable="false"
                    show-icon
                    :title="claimErrors[scope.row.id]"
                    class="claim-error"
                  />
                  <el-form
                    :ref="el => setClaimFormRef(scope.row.id, el)"
                    :model="claimForms[scope.row.id] || {}"
                    :rules="claimRules"
                    label-width="92px"
                    class="claim-form"
                    @submit.prevent
                  >
                    <el-row :gutter="12">
                      <el-col :span="12">
                        <el-form-item label="领取人" prop="claimerName">
                          <el-input
                            v-model="claimForms[scope.row.id].claimerName"
                            maxlength="100"
                            placeholder="请填写领取人姓名"
                          />
                        </el-form-item>
                      </el-col>
                      <el-col :span="12">
                        <el-form-item label="经办值班员" prop="operator">
                          <el-input
                            v-model="claimForms[scope.row.id].operator"
                            maxlength="100"
                            placeholder="请填写经办值班员"
                          />
                        </el-form-item>
                      </el-col>
                    </el-row>
                    <el-form-item label="核验信息" prop="claimerVerify">
                      <el-input
                        v-model="claimForms[scope.row.id].claimerVerify"
                        type="textarea"
                        :rows="2"
                        maxlength="200"
                        show-word-limit
                        placeholder="证件号/学工号及核对结果，如 学生证 20230101，核对包内校园卡一致"
                      />
                    </el-form-item>
                    <el-form-item label="领取结论" prop="claimConclusion">
                      <el-input
                        v-model="claimForms[scope.row.id].claimConclusion"
                        type="textarea"
                        :rows="2"
                        maxlength="500"
                        show-word-limit
                        placeholder="如 核验通过，物品完好交还领取人"
                      />
                    </el-form-item>
                    <el-form-item class="claim-actions">
                      <el-button
                        type="primary"
                        :loading="!!claimLoading[scope.row.id]"
                        @click="submitClaim(scope.row)"
                      >确认领取闭环</el-button>
                      <span class="claim-tip">领取必须核验领取人身份并填写领取结论；闭环后该桌椅方可再开高峰占座</span>
                    </el-form-item>
                  </el-form>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="itemNo" label="待领单号" width="190" />
            <el-table-column prop="itemName" label="物品名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="assetCode" label="桌椅编号" width="110" />
            <el-table-column prop="storageLocation" label="暂存位置" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="110">
              <template #default="scope">
                <el-button link type="primary" size="small" @click="toggleClaimRow(scope.row)">
                  办理领取
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!detail.pendingLostItems || detail.pendingLostItems.length === 0"
            description="该分区暂无待领取遗失物品"
          />

          <h3 class="detail-subtitle">桌椅明细（{{ detail.deskChairs?.length || 0 }}）</h3>
          <el-table :data="detail.deskChairs || []" border size="small">
            <el-table-column prop="assetCode" label="资产编号" width="130" />
            <el-table-column prop="capacity" label="容纳人数" width="90" align="center" />
            <el-table-column prop="dimensions" label="尺寸">
              <template #default="scope">{{ scope.row.dimensions || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="scope">
                <el-tag
                  size="small"
                  :type="scope.row.occupied ? 'warning' : (scope.row.status === 1 ? 'success' : 'danger')"
                >
                  {{ scope.row.occupied ? '占座占用' : (scope.row.status === 1 ? '可用' : '停用') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="160">
              <template #default="scope">
                <template v-if="scope.row.tags && scope.row.tags.length">
                  <el-tag
                    v-for="tag in scope.row.tags"
                    :key="tag.id"
                    size="small"
                    class="desk-tag"
                    :style="{ backgroundColor: tag.tagColor }"
                  >{{ tag.tagName }}</el-tag>
                </template>
                <span v-else class="no-tag">-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!detail.deskChairs || detail.deskChairs.length === 0"
            description="该分区暂无桌椅"
          />

          <h3 class="detail-subtitle">最近调区变更（{{ detail.recentChanges?.length || 0 }}）</h3>
          <el-table :data="detail.recentChanges || []" border size="small">
            <el-table-column prop="assetCode" label="资产编号" width="120" />
            <el-table-column label="原分区" min-width="110">
              <template #default="scope">{{ scope.row.oldAreaName || '-' }}</template>
            </el-table-column>
            <el-table-column label="新分区" min-width="110">
              <template #default="scope">{{ scope.row.newAreaName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="changeReason" label="变更原因" min-width="130">
              <template #default="scope">{{ scope.row.changeReason || '-' }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column label="时间" width="170">
              <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!detail.recentChanges || detail.recentChanges.length === 0"
            description="暂无调区变更记录"
          />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, h, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, ArrowRight, ArrowRightBold, CircleCloseFilled } from '@element-plus/icons-vue'
import { dashboardApi, lostItemApi } from '../api'
import { isAreaClosed, formatClosedUntil, listClosedAreas } from '../utils/areaClosure'

// 闭馆是否到期以当前时刻为准。页面长时间挂着不关时，过了闭馆结束时刻也要自动归零、
// 自动摘牌，不依赖手动刷新：每 30 秒把当前时刻推进一次，闭馆数字/横幅随之重算
const now = ref(new Date())
const nowTimer = setInterval(() => { now.value = new Date() }, 30000)
onBeforeUnmount(() => clearInterval(nowTimer))

// 简单柱状条：使用渲染函数避免再注册组件文件，原生 title 作为悬浮提示
const BarItem = {
  props: {
    item: { type: Object, required: true },
    max: { type: Number, default: 1 }
  },
  setup(props) {
    return () => {
      const count = props.item.changeCount || 0
      const height = count > 0 && props.max > 0
        ? Math.max(6, Math.round((count / props.max) * 160))
        : 2
      return h('div', { class: 'bar-item' }, [
        h('div', {
          class: 'bar-tooltip-wrap',
          title: `${props.item.date}：${count} 次调区`
        }, [
          h('div', {
            class: count > 0 ? 'bar bar-active' : 'bar',
            style: { height: height + 'px' }
          }),
          h('div', { class: 'bar-count' }, String(count))
        ]),
        h('div', { class: 'bar-date' }, props.item.date.slice(5))
      ])
    }
  }
}
const barItem = BarItem

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

// ---- 分区容量统计 ----
const areaStats = ref([])
const statsLoading = ref(false)
const statsError = ref('')

const loadStats = async () => {
  statsLoading.value = true
  statsError.value = ''
  try {
    areaStats.value = await dashboardApi.getAreaCapacityStats()
  } catch (e) {
    areaStats.value = []
    statsError.value = e?.message || '网络异常，请稍后重试'
  } finally {
    statsLoading.value = false
  }
  // 抽屉处于打开状态时，刷新要同步更新抽屉里的待领清单，避免卡片件数已减少
  // 而抽屉仍挂着已领取闭环的旧单号（空了应显示 0 与暂无待领）
  if (detailVisible.value) {
    await loadDetail({ silent: true })
  }
}

// ---- 调区变更趋势 ----
const today = new Date()
const monthAgo = new Date()
monthAgo.setDate(today.getDate() - 29)
const fmt = d => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const dateRange = ref([fmt(monthAgo), fmt(today)])
const trendAreaId = ref(null)
const trendData = ref([])
const trendLoading = ref(false)
const trendError = ref('')

const trendMax = computed(() =>
  trendData.value.reduce((max, item) => Math.max(max, item.changeCount), 0)
)

const loadTrend = async () => {
  if (!dateRange.value || dateRange.value.length !== 2) {
    trendData.value = []
    return
  }
  trendLoading.value = true
  trendError.value = ''
  try {
    trendData.value = await dashboardApi.getChangeTrend({
      startDate: dateRange.value[0],
      endDate: dateRange.value[1],
      areaId: trendAreaId.value
    })
  } catch (e) {
    trendData.value = []
    trendError.value = e?.message || '网络异常，请稍后重试'
  } finally {
    trendLoading.value = false
  }
}

const disableFutureDate = (date) => date.getTime() > Date.now()

// ---- 全馆概览汇总 ----
const overview = computed(() => {
  const summary = {
    areaCount: areaStats.value.length,
    totalCount: 0,
    availableCount: 0,
    occupiedCount: 0,
    disabledCount: 0,
    totalCapacity: 0,
    trendCount: 0
  }
  areaStats.value.forEach(area => {
    summary.totalCount += area.totalCount || 0
    summary.availableCount += area.availableCount || 0
    summary.occupiedCount += area.occupiedCount || 0
    summary.disabledCount += area.disabledCount || 0
    summary.totalCapacity += area.totalCapacity || 0
  })
  summary.trendCount = trendData.value.reduce((sum, item) => sum + item.changeCount, 0)
  return summary
})

// ---- 今晚闭馆分区 ----
// 顶部数字卡与点开后的闭馆清单同一口径：只含结束时刻晚于当前时刻的分区，到期自动掉到 0
const closedAreas = computed(() => listClosedAreas(areaStats.value, now.value))
const closedListVisible = ref(false)

// 从闭馆清单点进对应分区下钻：关掉清单弹层后打开该分区抽屉，抽屉里能看到“闭馆至几点”
const openClosedArea = async (areaId) => {
  closedListVisible.value = false
  await openDetail(areaId)
}

// 卡片/抽屉的闭馆判定统一跟随上面的当前时刻，保证到期不刷新也自动摘牌
const cardClosed = area => isAreaClosed(area, now.value)

// ---- 分区下钻 ----
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref(null)
const detailAreaId = ref(null)

const detailTitle = computed(() => {
  if (!detail.value) return '分区明细'
  return `${detail.value.areaCode} ${detail.value.areaName}`
})

const openDetail = async (areaId) => {
  detailAreaId.value = areaId
  detailVisible.value = true
  await loadDetail()
}

const loadDetail = async ({ silent = false } = {}) => {
  if (!detailAreaId.value) return
  // 跟随刷新静默更新时保留旧内容且不闪整屏 loading，避免抽屉先被清空再渲染出现闪烁
  if (!silent) {
    detailLoading.value = true
    detailError.value = ''
    detail.value = null
  }
  try {
    detail.value = await dashboardApi.getAreaCapacityDetail(detailAreaId.value, 10)
    detailError.value = ''
    syncClaimForms(detail.value.pendingLostItems || [])
  } catch (e) {
    if (silent) {
      // 静默刷新失败：保留抽屉内原有内容，仅轻提示，不打断值班员查看
      ElMessage.error(e?.message || '分区明细刷新失败，请稍后重试')
    } else {
      detailError.value = e?.message || '网络异常，请稍后重试'
    }
  } finally {
    if (!silent) {
      detailLoading.value = false
    }
  }
}

const reloadDetail = () => loadDetail()

// ---- 抽屉内直接办理遗失物品领取闭环 ----
// 每行独立表单：领取人、核验信息、领取结论、经办值班员，缺项走行内校验，
// 提交失败原因就地显示在展开面板内，不弹走整页
const lostTableRef = ref()
const claimForms = reactive({})
const claimFormRefs = new Map()
const claimLoading = reactive({})
const claimErrors = reactive({})

const claimRules = {
  claimerName: [{ required: true, message: '请填写领取人', trigger: 'blur' }],
  claimerVerify: [{ required: true, message: '请填写领取人核验信息', trigger: 'blur' }],
  claimConclusion: [{ required: true, message: '请填写领取结论', trigger: 'blur' }],
  operator: [{ required: true, message: '请填写经办值班员', trigger: 'blur' }]
}

const emptyClaimForm = () => ({
  claimerName: '',
  claimerVerify: '',
  claimConclusion: '',
  operator: ''
})

const setClaimFormRef = (id, el) => {
  if (el) {
    claimFormRefs.set(id, el)
  } else {
    claimFormRefs.delete(id)
  }
}

// 明细刷新后：为新出现的待领单建空表单，已闭环消失的单清掉对应状态
const syncClaimForms = (items) => {
  const liveIds = new Set(items.map(item => item.id))
  items.forEach(item => {
    if (!claimForms[item.id]) {
      claimForms[item.id] = emptyClaimForm()
    }
  })
  Object.keys(claimForms).forEach(id => {
    if (!liveIds.has(Number(id))) {
      delete claimForms[id]
      delete claimLoading[id]
      delete claimErrors[id]
      claimFormRefs.delete(Number(id))
    }
  })
}

const toggleClaimRow = row => {
  claimErrors[row.id] = ''
  lostTableRef.value?.toggleRowExpansion(row)
}

const submitClaim = async row => {
  const formRef = claimFormRefs.get(row.id)
  if (!formRef || !claimForms[row.id]) return
  try {
    await formRef.validate()
  } catch (e) {
    // 缺项提示已在各字段下方展示，不离开抽屉
    return
  }
  const form = claimForms[row.id]
  claimLoading[row.id] = true
  claimErrors[row.id] = ''
  try {
    await lostItemApi.claim(row.id, {
      claimerName: form.claimerName,
      claimerVerify: form.claimerVerify,
      claimConclusion: form.claimConclusion,
      operator: form.operator
    })
  } catch (e) {
    // 失败原因就地呈现在该单面板内（如已被他人先领取、服务端校验不通过）
    claimErrors[row.id] = e?.message || '领取登记失败，请稍后重试'
    claimLoading[row.id] = false
    // 该单已被他人先领取或已不存在：展示原因的同时静默对账，
    // 让清单与卡片件数回归真实状态，值班员无需离开抽屉
    const msg = claimErrors[row.id]
    if (msg.includes('已领取闭环') || msg.includes('遗失登记单不存在')) {
      reconcileStatsSilent()
    }
    return
  }
  claimLoading[row.id] = false
  ElMessage.success(`遗失单 ${row.itemNo} 已领取闭环`)

  // 成功后抽屉内立即收口：清单移除该单、抽屉件数与标题同步减少直至 0
  const items = detail.value.pendingLostItems || []
  detail.value.pendingLostItems = items.filter(item => item.id !== row.id)
  detail.value.pendingLostCount = detail.value.pendingLostItems.length
  delete claimForms[row.id]
  delete claimLoading[row.id]
  claimFormRefs.delete(row.id)

  // 卡片待领数字同步减少；后台对账静默执行，失败也不打断抽屉操作
  const stat = areaStats.value.find(area => area.areaId === detail.value.areaId)
  if (stat) {
    stat.pendingLostCount = Math.max(0, (stat.pendingLostCount ?? 1) - 1)
  }
  reconcileStatsSilent()
}

// 后台静默对账卡片统计，不影响抽屉当前内容与 loading
const reconcileStatsSilent = async () => {
  try {
    areaStats.value = await dashboardApi.getAreaCapacityStats()
    await loadDetail({ silent: true })
  } catch (e) {
    // 本地已先行扣减，对账失败保留本地结果，等待下次刷新
  }
}

onMounted(() => {
  loadStats()
  loadTrend()
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
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 600;
}

.overview-row {
  margin-bottom: 4px;
}

.overview-card {
  margin-bottom: 16px;
}

.overview-label {
  font-size: 13px;
  color: #909399;
}

.overview-value {
  font-size: 30px;
  font-weight: 700;
  color: #303133;
  line-height: 1.4;
}

.overview-value.primary {
  color: #409eff;
}

.overview-value.warning {
  color: #e6a23c;
}

.overview-sub {
  font-size: 12px;
  color: #c0c4cc;
}

.section-card {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
}

.section-tip {
  font-size: 12px;
  color: #909399;
}

.trend-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trend-area-select {
  width: 160px;
}

/* 分区卡片 */
.area-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.area-card-closed {
  background: #fdf6f6;
  border-color: #f9c7c8;
}

.closed-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: -4px 0 12px;
  padding: 6px 10px;
  border-radius: 4px;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 12px;
  font-weight: 600;
}

.detail-closed-alert {
  margin-bottom: 16px;
}

.area-card:hover {
  border-color: #409eff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.area-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.area-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.area-code {
  background: #409eff;
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  flex-shrink: 0;
}

.area-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.area-metrics {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

.metric-value.available {
  color: #67c23a;
}

.metric-value.occupied {
  color: #e6a23c;
}

.metric-value.disabled {
  color: #f56c6c;
}

.metric-value.primary {
  color: #409eff;
}

.metric-value.lost {
  color: #c45656;
}

.metric-label {
  font-size: 12px;
  color: #909399;
}

.area-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
  margin-bottom: 10px;
}

.area-tag {
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  line-height: 1.5;
}

.no-tag {
  font-size: 12px;
  color: #c0c4cc;
}

.area-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #409eff;
}

/* 趋势柱状图 */
.trend-body {
  min-height: 220px;
}

.trend-scroll {
  overflow-x: auto;
}

.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  min-height: 200px;
  padding: 12px 8px 0;
}

.trend-scroll .trend-chart {
  min-width: max-content;
}

.bar-item {
  flex: 1;
  min-width: 26px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.bar-tooltip-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 170px;
  width: 100%;
}

.bar {
  width: 60%;
  max-width: 24px;
  background: #dcdfe6;
  border-radius: 3px 3px 0 0;
  transition: background 0.2s;
}

.bar-active {
  background: linear-gradient(180deg, #79bbff 0%, #409eff 100%);
}

.bar-tooltip-wrap:hover .bar-active {
  background: linear-gradient(180deg, #a0cfff 0%, #79bbff 100%);
}

.bar-tooltip-wrap:hover::after {
  content: attr(data-tip);
}

.bar-count {
  font-size: 11px;
  color: #606266;
  line-height: 1;
}

.bar-date {
  font-size: 10px;
  color: #909399;
  transform: rotate(-45deg);
  transform-origin: center top;
  white-space: nowrap;
  height: 38px;
}

/* 下钻明细 */
.detail-desc {
  margin-bottom: 20px;
}

.detail-subtitle {
  font-size: 15px;
  margin: 20px 0 10px;
}

/* 抽屉内待领行直接办理领取 */
.lost-table {
  margin-bottom: 10px;
}

.claim-panel {
  padding: 12px 16px 4px;
  background: #fafbfc;
}

.claim-form {
  max-width: 860px;
}

.claim-error {
  margin-bottom: 12px;
}

.claim-actions {
  margin-bottom: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.claim-tip {
  font-size: 12px;
  color: #e6a23c;
}

.desk-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.available {
  color: #67c23a;
  font-weight: 600;
}

.occupied {
  color: #e6a23c;
  font-weight: 600;
}

.disabled {
  color: #f56c6c;
  font-weight: 600;
}

.primary {
  color: #409eff;
  font-weight: 600;
}
</style>
