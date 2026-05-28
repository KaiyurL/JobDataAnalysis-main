<template>
  <div class="u-stack">
    <div class="jd-page-head">
      <div class="jd-page-head__title jd-page-head__title-text u-title">🔧 数据管理</div>
      <div class="jd-page-head__desc">监控爬虫状态、管理数据更新和自定义爬取配置</div>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：数据概况 + 配置表单 -->
      <el-col :xs="24" :lg="12">
        <DataManagementLeftPanel
          :loading="loading"
          :saving-config="savingConfig"
          :overview="overview"
          :config="config"
          :all-cities="allCities"
          :city-code-rows="cityCodeRows"
          :new-keyword="newKeyword"
          :selected-city="selectedCity"
          :new-city-code-name="newCityCodeName"
          :new-city-code-value="newCityCodeValue"
          :config-changed="configChanged"
          :get-status-type="getStatusType"
          :get-status-text="getStatusText"
          :load-data="loadData"
          :save-config="saveConfig"
          :reset-config="resetConfig"
          :add-keyword="addKeyword"
          :remove-keyword="removeKeyword"
          :add-city="addCity"
          :remove-city="removeCity"
          :add-city-code="addCityCode"
          :remove-city-code="removeCityCode"
          @update:new-keyword="newKeyword = $event"
          @update:selected-city="selectedCity = $event"
          @update:new-city-code-name="newCityCodeName = $event"
          @update:new-city-code-value="newCityCodeValue = $event"
        />
      </el-col>

      <!-- 右侧：操作区 + 日志 -->
      <el-col :xs="24" :lg="12">
        <DataManagementRightPanel
          :updating="updating"
          :button-disabled="buttonDisabled"
          :can-confirm-login="canConfirmLogin"
          :can-stop="canStop"
          :expected-requests="expectedRequests"
          :config="config"
          :logs="logs"
          :set-log-container="setLogContainer"
          :reindexing="reindexing"
          :reindex-result="reindexResult == null ? undefined : reindexResult"
          :stopping="stopping"
          :start-update="startUpdate"
          :confirm-login="confirmLogin"
          :stop-update="stopUpdate"
          :handle-reindex="handleReindex"
          :clear-logs="clearLogs"
        />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { useDataManagement } from '@/features/data-management/composables/useDataManagement.js'
import DataManagementLeftPanel from '@/features/data-management/components/DataManagementLeftPanel.vue'
import DataManagementRightPanel from '@/features/data-management/components/DataManagementRightPanel.vue'

const {
  loading,
  updating,
  savingConfig,
  buttonDisabled,
  overview,
  logs,
  logContainer,
  newKeyword,
  selectedCity,
  newCityCodeName,
  newCityCodeValue,
  config,
  allCities,
  cityCodeRows,
  getStatusType,
  getStatusText,
  configChanged,
  canConfirmLogin,
  canStop,
  expectedRequests,
  clearLogs,
  stopping,
  reindexing,
  reindexResult,
  handleReindex,
  loadData,
  saveConfig,
  resetConfig,
  addKeyword,
  removeKeyword,
  addCity,
  removeCity,
  addCityCode,
  removeCityCode,
  confirmLogin,
  stopUpdate,
  startUpdate
} = useDataManagement()

/**
 * 将日志容器 DOM 引用回传给 composable，用于自动滚动等行为。
 *
 * @param {HTMLElement|null} el 日志容器元素
 */
const setLogContainer = (el) => {
  logContainer.value = el
}
</script>
