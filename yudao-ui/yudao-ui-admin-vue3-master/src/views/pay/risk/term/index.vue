<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px" label-width="80px">
      <el-form-item label="风险词" prop="term">
        <el-input
          v-model="queryParams.term"
          class="!w-220px"
          clearable
          placeholder="模糊搜索"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="queryParams.category" class="!w-160px" clearable placeholder="全部">
          <el-option v-for="c in TERM_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源" prop="sourceType">
        <el-select v-model="queryParams.sourceType" class="!w-160px" clearable placeholder="全部">
          <el-option label="人工录入" value="MANUAL" />
          <el-option label="评估自动" value="AUTO_ASSESS" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-120px" clearable placeholder="全部">
          <el-option label="启用" :value="0" />
          <el-option label="禁用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon class="mr-5px" icon="ep:search" />
          搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          重置
        </el-button>
        <el-button type="primary" plain @click="openForm('create')">
          <Icon class="mr-5px" icon="ep:plus" />
          新增
        </el-button>
        <el-button link type="primary" @click="goAssess">返回风险评估</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" prop="id" width="80" align="center" />
      <el-table-column label="风险词" prop="term" min-width="200" show-overflow-tooltip />
      <el-table-column label="分类" prop="category" width="110" align="center">
        <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
      </el-table-column>
      <el-table-column label="来源" prop="sourceType" width="100" align="center">
        <template #default="{ row }">{{ TERM_SOURCE_LABEL[row.sourceType] || row.sourceType }}</template>
      </el-table-column>
      <el-table-column label="累计命中" prop="hitCount" width="90" align="center" />
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="首次出现" prop="firstSeenTime" width="170" align="center" :formatter="dateFormatter" />
      <el-table-column label="最近命中" prop="lastHitTime" width="170" align="center" :formatter="dateFormatter" />
      <el-table-column label="说明" prop="description" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm('update', row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <TermForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import {
  deletePayRiskTerm,
  getPayRiskTermPage,
  TERM_CATEGORIES,
  TERM_SOURCE_LABEL,
  type PayRiskTermVO
} from '@/api/pay/risk/term'
import TermForm from './TermForm.vue'

defineOptions({ name: 'PayRiskTerm' })

const router = useRouter()
const message = useMessage()

const loading = ref(false)
const total = ref(0)
const list = ref<PayRiskTermVO[]>([])
const queryFormRef = ref()
const formRef = ref()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  term: undefined as string | undefined,
  category: undefined as string | undefined,
  sourceType: undefined as string | undefined,
  status: undefined as number | undefined
})

const categoryLabel = (value?: string) =>
  TERM_CATEGORIES.find(c => c.value === value)?.label || value || '-'

const getList = async () => {
  loading.value = true
  try {
    const data = await getPayRiskTermPage(queryParams)
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const openForm = (type: 'create' | 'update', row?: PayRiskTermVO) => {
  formRef.value?.open(type, row)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await deletePayRiskTerm(id)
    message.success('删除成功')
    await getList()
  } catch {
    /* cancel */
  }
}

const goAssess = () => {
  router.push({ name: 'PayRiskAssess' })
}

onMounted(() => {
  getList()
})
</script>
