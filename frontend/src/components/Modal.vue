<script setup>
import { ref, watch, onMounted } from 'vue'
const props = defineProps({ open: Boolean, title: String, wide: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
function sync() { if (!dialog.value) return; props.open ? dialog.value.showModal() : dialog.value.close() }
watch(() => props.open, sync)
onMounted(sync)
</script>
<template>
  <Teleport to="body"><dialog ref="dialog" :class="['modal', { wide }]" @cancel.prevent="emit('close')" aria-modal="true" :aria-label="title">
    <header class="modal-head"><h2>{{ title }}</h2><button type="button" class="plain" aria-label="关闭弹窗" @click="emit('close')">关闭</button></header>
    <slot />
  </dialog></Teleport>
</template>
