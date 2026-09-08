<script setup>
import { computed } from 'vue'
const props = defineProps({ total: { type: Number, default: 0 }, page: { type: Number, default: 1 }, size: { type: Number, default: 20 } })
const emit = defineEmits(['change'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>
<template><div class="pagination no-print"><span>共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span><div class="actions"><button :disabled="page <= 1" @click="emit('change', page - 1)">上一页</button><button :disabled="page >= pages" @click="emit('change', page + 1)">下一页</button></div></div></template>
