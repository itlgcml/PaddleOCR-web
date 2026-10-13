<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { fetchCcPage } from '@/api/approval-runtime'
import type { CcVO } from '@/types/approval'

/**
 * 抄送列表面板（待阅 / 已阅共用）：read=false 待阅（查阅进入详情页自动标记已读），read=true 已阅。
 */
const props = defineProps<{ read: boolean }>()

const router = useRouter()

const loading = ref(false)
const records = shallowRef<CcVO[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const page = await fetchCcPage(props.read, {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
    })
    records.value = page.records
    total.value = page.total
  } catch {
    // 失败提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.pageNum = 1
  void load()
}

function onReset(): void {
  query.keyword = ''
  onSearch()
}

/** 查阅：带 ccId 进入详情页，详情页 onMounted 自动标记已读 */
function goDetail(row: CcVO): void {
  void router.push(`/approval/detail/${row.instanceId}?ccId=${row.id}`)
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="流程名称 / 业务标题"
            clearable
            class="toolbar-input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="processName" label="流程名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="bizTitle" label="业务标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="fromUserName" label="发起人" width="110" />
      <el-table-column prop="comment" label="抄送备注" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.comment || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="抄送时间" width="170" />
      <el-table-column v-if="props.read" prop="readTime" label="已阅时间" width="170">
        <template #default="{ row }">{{ row.readTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="goDetail(row)">{{ props.read ? '查看' : '查阅' }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="props.read ? '暂无已阅记录' : '暂无待阅抄送'" :image-size="72" />
      </template>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="onSearch"
      />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 4px;
}

.toolbar-input {
  width: 240px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
