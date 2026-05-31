<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="88px">
      <el-form-item label="风险词" prop="term">
        <el-input v-model="formData.term" placeholder="骗子聊天记录中的典型话术，如「马上转账到安全账户」" maxlength="256" show-word-limit />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="formData.category" class="!w-full" placeholder="请选择分类">
          <el-option v-for="c in TERM_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :label="0">启用</el-radio>
          <el-radio :label="1">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="说明" prop="description">
        <el-input v-model="formData.description" type="textarea" :rows="3" maxlength="512" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { createPayRiskTerm, TERM_CATEGORIES, updatePayRiskTerm, type PayRiskTermVO } from '@/api/pay/risk/term'

const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitting = ref(false)
const formType = ref<'create' | 'update'>('create')
const formRef = ref()

const formData = reactive<PayRiskTermVO>({
  id: undefined,
  term: '',
  category: 'OTHER',
  status: 0,
  description: ''
})

const formRules = {
  term: [{ required: true, message: '请输入风险词', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const emit = defineEmits<{ (e: 'success'): void }>()

const resetForm = () => {
  formData.id = undefined
  formData.term = ''
  formData.category = 'OTHER'
  formData.status = 0
  formData.description = ''
  formRef.value?.resetFields()
}

const open = (type: 'create' | 'update', row?: PayRiskTermVO) => {
  formType.value = type
  dialogTitle.value = type === 'create' ? '新增风险词' : '编辑风险词'
  resetForm()
  if (type === 'update' && row) {
    Object.assign(formData, row)
  }
  dialogVisible.value = true
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (formType.value === 'create') {
      await createPayRiskTerm({ ...formData })
      message.success('新增成功')
    } else {
      await updatePayRiskTerm({ ...formData })
      message.success('更新成功')
    }
    dialogVisible.value = false
    emit('success')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
