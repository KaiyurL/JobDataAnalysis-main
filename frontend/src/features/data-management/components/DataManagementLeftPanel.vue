<template>
  <el-card class="stats-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>📊 数据概况</span>
        <el-button size="small" type="primary" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </template>

    <el-descriptions :column="1" border>
      <el-descriptions-item label="总数据量">
        <span class="stat-number">{{ overview.totalCount || 0 }}</span>
        条记录
      </el-descriptions-item>
      <el-descriptions-item label="本次开始时间">
        <span class="stat-text">{{ overview.lastStartTime || '未开始' }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="本次结束时间">
        <span class="stat-text">{{ overview.lastEndTime || (overview.status === 'running' ? '进行中' : '未知') }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="上次爬取时间">
        <span class="stat-text">{{ overview.lastCrawlTime || '未知' }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="爬虫状态">
        <el-tag :type="getStatusType(overview.status)">
          {{ getStatusText(overview.status) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="状态信息">
        <span class="status-message">{{ overview.lastMessage || '暂无' }}</span>
      </el-descriptions-item>
    </el-descriptions>
  </el-card>

  <el-card class="config-card u-mt-5" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>⚙️ 爬虫配置</span>
        <el-button size="small" type="success" :loading="savingConfig" :disabled="!configChanged" @click="saveConfig"> 保存配置 </el-button>
      </div>
    </template>

    <el-form :model="config" label-width="120px">
      <el-form-item label="数据源">
        <el-select v-model="config.platform" style="width: 100%">
          <el-option label="BOSS直聘" value="boss" />
          <el-option label="前程无忧" value="51job" />
          <el-option label="全部" value="both" />
        </el-select>
        <div class="form-tip">选择要爬取的数据平台</div>
      </el-form-item>

      <el-form-item label="浏览器">
        <el-select v-model="config.browser" style="width: 100%">
          <el-option label="自动" value="auto" />
          <el-option label="Edge" value="edge" />
          <el-option label="Chrome" value="chrome" />
        </el-select>
        <div class="form-tip">用于启动 DrissionPage 浏览器（若遇风控，可尝试切换）</div>
      </el-form-item>

      <el-form-item label="岗位关键词">
        <div class="keywords-input">
          <el-tag
            v-for="(keyword, index) in config.keywords"
            :key="index"
            closable
            style="margin-right: 8px; margin-bottom: 8px"
            @close="removeKeyword(index)"
          >
            {{ keyword }}
          </el-tag>
        </div>
        <div style="display: flex; gap: 10px; margin-top: 10px">
          <el-input
            :model-value="newKeyword"
            placeholder="输入关键词，按回车添加"
            @update:model-value="(v) => emit('update:new-keyword', v)"
            @keyup.enter="addKeyword"
          />
          <el-button type="primary" @click="addKeyword">添加</el-button>
        </div>
        <div class="form-tip">建议一个关键词爬取</div>
      </el-form-item>

      <el-form-item label="目标城市">
        <div class="cities-input">
          <el-tag
            v-for="(city, index) in config.cities"
            :key="index"
            closable
            style="margin-right: 8px; margin-bottom: 8px"
            @close="removeCity(index)"
          >
            {{ city }}
          </el-tag>
        </div>
        <el-select
          :model-value="selectedCity"
          placeholder="选择/搜索城市"
          filterable
          style="width: 100%; margin-top: 10px"
          @update:model-value="(v) => emit('update:selected-city', v)"
          @change="addCity"
        >
          <el-option v-for="city in allCities" :key="city" :label="city" :value="city" />
        </el-select>
        <div class="form-tip">从下拉选择或直接输入搜索</div>
      </el-form-item>

      <el-form-item v-if="config.platform !== '51job'" label="BOSS滚动次数">
        <el-input-number v-model="config.pages_per_keyword" :min="1" :max="200" />
        <span style="margin-left: 10px">次/关键词</span>
        <div class="form-tip">用于滚动加载岗位卡片（不是传统页码）</div>
      </el-form-item>

      <el-form-item v-if="config.platform !== 'boss'" label="前程无忧页数">
        <el-input-number v-model="config.pages_per_city_51job" :min="1" :max="20" />
        <span style="margin-left: 10px">页/城市/关键词</span>
      </el-form-item>

      <el-form-item v-if="config.platform !== 'boss'" label="前程无忧城市编码">
        <el-table :data="cityCodeRows" size="small" border style="width: 100%">
          <el-table-column prop="name" label="城市" width="140" />
          <el-table-column label="编码" min-width="160">
            <template #default="{ row }">
              <el-input v-model="config.city_codes_51job[row.name]" size="small" placeholder="例如 010000" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button type="danger" size="small" link @click="removeCityCode(row.name)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div style="display: flex; gap: 10px; margin-top: 10px; width: 100%">
          <el-input
            :model-value="newCityCodeName"
            placeholder="新增城市名（如 北京）"
            @update:model-value="(v) => emit('update:new-city-code-name', v)"
          />
          <el-input
            :model-value="newCityCodeValue"
            placeholder="新增编码（如 010000）"
            @update:model-value="(v) => emit('update:new-city-code-value', v)"
          />
          <el-button type="primary" @click="addCityCode">添加</el-button>
        </div>
        <div class="form-tip">只对前程无忧有效；用于 jobArea 参数</div>
      </el-form-item>

      <el-form-item label="请求页面延迟">
        <el-input-number v-model="config.delay_min" :min="1" :max="20" style="width: 120px" />
        <span style="margin: 0 10px">-</span>
        <el-input-number v-model="config.delay_max" :min="config.delay_min" :max="30" style="width: 120px" />
        <span style="margin-left: 10px">秒</span>
      </el-form-item>

      <el-form-item>
        <el-button type="warning" @click="resetConfig">恢复默认</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
/**
 * 数据管理页左侧面板（概况 + 配置）
 *
 * 设计目标：
 * - 将 DataManagement.vue 的左侧大块模板拆出；
 * - 所有状态与动作来自 composable/useDataManagement，本组件只负责表单绑定与触发回调；
 * - 保持原页面结构与交互行为一致。
 */
defineProps({
  loading: { type: Boolean, required: true },
  savingConfig: { type: Boolean, required: true },
  overview: { type: Object, required: true },
  config: { type: Object, required: true },
  allCities: { type: Array, required: true },
  cityCodeRows: { type: Array, required: true },
  newKeyword: { type: String, required: true },
  selectedCity: { type: String, required: true },
  newCityCodeName: { type: String, required: true },
  newCityCodeValue: { type: String, required: true },
  configChanged: { type: Boolean, required: true },
  getStatusType: { type: Function, required: true },
  getStatusText: { type: Function, required: true },
  loadData: { type: Function, required: true },
  saveConfig: { type: Function, required: true },
  resetConfig: { type: Function, required: true },
  addKeyword: { type: Function, required: true },
  removeKeyword: { type: Function, required: true },
  addCity: { type: Function, required: true },
  removeCity: { type: Function, required: true },
  addCityCode: { type: Function, required: true },
  removeCityCode: { type: Function, required: true }
})

const emit = defineEmits(['update:new-keyword', 'update:selected-city', 'update:new-city-code-name', 'update:new-city-code-value'])
</script>
