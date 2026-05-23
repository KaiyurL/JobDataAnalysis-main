<template>
  <div class="u-stack">
    <div class="u-card u-card-pad">
      <div class="u-row u-row-start">
        <div class="u-stack u-gap-2">
          <div class="u-inline">
            <span class="u-pill">数据源</span>
            <div class="jd-seg">
              <router-link to="/job-analysis/boss">BOSS直聘</router-link>
              <router-link to="/job-analysis/51job">前程无忧</router-link>
            </div>
          </div>
        </div>

        <div class="u-stack u-gap-2">
          <div class="u-inline">
            <span class="u-pill">专项分析</span>
            <div class="jd-seg">
              <router-link to="/job-analysis/skills">技能雷达</router-link>
              <router-link to="/job-analysis/salary">薪资预测</router-link>
              <router-link to="/job-analysis/insight">公司洞察</router-link>
            </div>
          </div>
        </div>
      </div>
    </div>

    <router-view v-slot="{ Component }">
      <transition name="jd-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<script setup>
</script>
