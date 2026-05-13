<script setup lang="ts">
import { ref, computed } from 'vue'
import { uploadImage } from '@/api/image'
import { ElMessage } from 'element-plus'
import IconPlus from '@/components/icons/IconPlus.vue'
import IconClose from '@/components/icons/IconClose.vue'

interface Props {
  accountId: number
  modelValue?: string
  max?: number
  maxFileSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  max: 5,
  maxFileSize: 10
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const imageUrls = computed(() => {
  if (!props.modelValue || !props.modelValue.trim()) return [] as string[]
  return props.modelValue.split(',').map((s: string) => s.trim()).filter((s: string) => s)
})

const canAdd = computed(() => imageUrls.value.length < props.max)

const uploadingIndex = ref(-1)
const uploadProgress = ref(0)
const draggingIndex = ref<number | null>(null)

const triggerFileInput = (index: number) => {
  const input = document.getElementById(`multi-img-input-${index}`) as HTMLInputElement
  input?.click()
}

const handleFileChange = async (index: number, event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (!props.accountId) {
    ElMessage.error('请先选择账号')
    target.value = ''
    return
  }

  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    target.value = ''
    return
  }

  const maxSize = props.maxFileSize * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error(`图片大小不能超过${props.maxFileSize}MB`)
    target.value = ''
    return
  }

  uploadingIndex.value = index
  uploadProgress.value = 0

  try {
    const progressInterval = setInterval(() => {
      if (uploadProgress.value < 90) uploadProgress.value += 10
    }, 100)

    const res = await uploadImage(props.accountId, file)

    clearInterval(progressInterval)
    uploadProgress.value = 100

    if (res && res.code === 200 && res.data) {
      const list = [...imageUrls.value]
      if (index < list.length) {
        list[index] = res.data
      } else {
        list.push(res.data)
      }
      emit('update:modelValue', list.join(','))
      ElMessage.success('图片上传成功')
    } else {
      throw new Error(res?.msg || '上传失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    setTimeout(() => {
      uploadingIndex.value = -1
      uploadProgress.value = 0
    }, 500)
  }

  target.value = ''
}

const addImage = () => {
  if (!canAdd.value) return
  if (!props.accountId) {
    ElMessage.error('请先选择账号')
    return
  }
  const nextIndex = imageUrls.value.length
  const input = document.getElementById(`multi-img-input-${nextIndex}`) as HTMLInputElement
  input?.click()
}

const removeImage = (index: number) => {
  const list = [...imageUrls.value]
  list.splice(index, 1)
  emit('update:modelValue', list.join(','))
}

const handleDragStart = (index: number) => {
  draggingIndex.value = index
}

const handleDrop = (index: number) => {
  if (draggingIndex.value === null || draggingIndex.value === index) return
  const list = [...imageUrls.value]
  const [moved] = list.splice(draggingIndex.value, 1)
  if (!moved) return
  list.splice(index, 0, moved)
  draggingIndex.value = null
  emit('update:modelValue', list.join(','))
}
</script>

<template>
  <div class="multi-img">
    <div class="multi-img__list">
      <div
        v-for="(url, index) in imageUrls"
        :key="index"
        class="multi-img__card"
        draggable="true"
        @dragstart="handleDragStart(index)"
        @dragover.prevent
        @drop.prevent="handleDrop(index)"
        @dragend="draggingIndex = null"
      >
        <img :src="url" class="multi-img__preview" @click="triggerFileInput(index)" />
        <span v-if="index === 0" class="multi-img__major">主图</span>
        <button class="multi-img__del" @click.stop="removeImage(index)">
          <IconClose />
        </button>
        <input
          :id="`multi-img-input-${index}`"
          type="file"
          accept="image/*"
          style="display: none"
          @change="handleFileChange(index, $event)"
        />
      </div>

      <div v-if="canAdd" class="multi-img__card multi-img__card--add" @click="addImage">
        <IconPlus />
        <span>添加图片</span>
      </div>
    </div>

    <input
      v-for="i in Math.max(0, (canAdd ? imageUrls.length + 1 : imageUrls.length) - imageUrls.length)"
      :key="'extra-' + i"
      :id="`multi-img-input-${imageUrls.length + i - 1}`"
      type="file"
      accept="image/*"
      style="display: none"
      @change="handleFileChange(imageUrls.length + i - 1, $event)"
    />

    <div v-if="uploadingIndex >= 0" class="multi-img__progress">
      <div class="multi-img__progress-bar">
        <div class="multi-img__progress-fill" :style="{ width: uploadProgress + '%' }"></div>
      </div>
      <span class="multi-img__progress-text">上传中 {{ uploadProgress }}%</span>
    </div>
  </div>
</template>

<style scoped>
.multi-img__list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.multi-img__card {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--app-border);
  background: var(--app-bg-muted);
  flex-shrink: 0;
}

.multi-img__preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  display: block;
}

.multi-img__del {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  border: none;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1;
  transition: background 0.2s;
  padding: 0;
}

.multi-img__major {
  position: absolute;
  left: 5px;
  top: 5px;
  padding: 2px 6px;
  border-radius: 5px;
  background: #0b5cff;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.multi-img__del:hover {
  background: rgba(0, 0, 0, 0.7);
}

.multi-img__del svg {
  width: 12px;
  height: 12px;
}

.multi-img__card--add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  border-style: dashed;
  color: var(--app-text-soft);
  font-size: 11px;
  transition: all 0.2s;
}

.multi-img__card--add:hover {
  border-color: var(--app-primary);
  color: var(--app-text-muted);
  background: var(--app-surface-hover);
}

.multi-img__card--add svg {
  width: 22px;
  height: 22px;
}

.multi-img__progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  width: 200px;
}

.multi-img__progress-bar {
  flex: 1;
  height: 4px;
  background: var(--app-border);
  border-radius: 2px;
  overflow: hidden;
}

.multi-img__progress-fill {
  height: 100%;
  background: #007aff;
  transition: width 0.2s;
}

.multi-img__progress-text {
  font-size: 11px;
  color: #007aff;
  white-space: nowrap;
}
</style>
