<script lang="ts" setup>
import { useConsoleTagQueryFetch, useUCTagQueryFetch } from "@/composables/use-tag";
import {
  IconArrowDown,
  IconClose,
  VDropdown,
  VEntity,
  VEntityContainer,
  VEntityField,
} from "@halo-dev/components";
import { computed, ref } from "vue";
import MingcuteTag2Line from "~icons/mingcute/tag-2-line";

/**
 * 发布表单标签多选下拉组件，支持模糊搜索与新建标签。
 */
const props = withDefaults(
  defineProps<{
    /** 已选标签列表 */
    modelValue?: string[];
    /** 触发器展示文案 */
    label?: string;
    /** 是否允许通过搜索关键词创建新标签 */
    allowCreate?: boolean;
    /** 标签数据源范围 */
    scope?: "console" | "uc";
  }>(),
  {
    modelValue: () => [],
    label: "标签",
    allowCreate: true,
    scope: "console",
  }
);

const emit = defineEmits<{
  (event: "update:modelValue", value: string[]): void;
}>();

/** 下拉内搜索关键词 */
const keyword = ref<string | undefined>(undefined);
/** 下拉实例，用于选择后关闭 */
const dropdown = ref();

const tagQueryFetch =
  props.scope === "uc" ? useUCTagQueryFetch : useConsoleTagQueryFetch;

const { data: tags, refetch } = tagQueryFetch({
  keyword,
});

const tagList = computed<string[]>(() => (tags.value as string[] | undefined) ?? []);

/** 根据关键词过滤后的标签候选 */
const searchResults = computed(() => {
  const list = tagList.value;
  const query = keyword.value?.trim().toLowerCase();
  if (!query) {
    return list;
  }
  return list.filter((tag) => tag.toLowerCase().includes(query));
});

/** 是否可基于当前关键词创建新标签 */
const canCreateTag = computed(() => {
  const value = keyword.value?.trim();
  if (!value || !props.allowCreate) {
    return false;
  }
  const exists = tagList.value.some((tag) => tag.toLowerCase() === value.toLowerCase());
  const selected = props.modelValue.some((tag) => tag.toLowerCase() === value.toLowerCase());
  return !exists && !selected;
});

/** 触发器摘要文案 */
const triggerLabel = computed(() => {
  if (!props.modelValue.length) {
    return props.label;
  }
  if (props.modelValue.length === 1) {
    return `${props.label}：${props.modelValue[0]}`;
  }
  return `${props.label}（${props.modelValue.length}）`;
});

/**
 * 切换标签选中状态。
 * @param tagName 标签名称
 */
function toggleTag(tagName: string) {
  const next = new Set(props.modelValue);
  if (next.has(tagName)) {
    next.delete(tagName);
  } else {
    next.add(tagName);
  }
  emit("update:modelValue", Array.from(next));
}

/**
 * 通过搜索关键词创建并选中标签。
 */
function createTagFromKeyword() {
  const value = keyword.value?.trim();
  if (!value || !canCreateTag.value) {
    return;
  }
  const next = new Set(props.modelValue);
  next.add(value);
  emit("update:modelValue", Array.from(next));
  keyword.value = undefined;
}

/**
 * 处理搜索框回车：优先创建新标签，否则选中唯一匹配项。
 */
function handleKeywordEnter() {
  if (canCreateTag.value) {
    createTagFromKeyword();
    return;
  }
  if (searchResults.value.length === 1) {
    toggleTag(searchResults.value[0]);
    dropdown.value?.hide();
  }
}

/** 清空全部已选标签 */
function clearAllTags(event: Event) {
  event.stopPropagation();
  emit("update:modelValue", []);
}
</script>

<template>
  <VDropdown ref="dropdown" :classes="[':uno: !p-0']" @show="refetch">
    <div
      class=":uno: tag-select-trigger group flex h-7 min-w-[6.5rem] max-w-[12rem] cursor-pointer select-none items-center gap-1 rounded-md border border-slate-200 bg-white px-2 text-xs text-slate-600 transition-colors duration-200 hover:border-blue-500 hover:text-slate-900"
      :class="{ ':uno: border-blue-500 bg-blue-50/60 text-blue-700': modelValue.length > 0 }"
      role="button"
      :aria-label="`${label}选择`"
    >
      <MingcuteTag2Line class=":uno: size-3.5 shrink-0 text-slate-400 group-hover:text-blue-600" />
      <span class=":uno: truncate">{{ triggerLabel }}</span>
      <span class=":uno: ml-auto text-sm text-slate-400">
        <IconArrowDown :class="{ ':uno: group-hover:hidden': modelValue.length > 0 }" />
        <IconClose
          v-if="modelValue.length > 0"
          class=":uno: hidden size-3.5 group-hover:block"
          @click="clearAllTags"
        />
      </span>
    </div>
    <template #popper>
      <div class=":uno: w-72 overflow-hidden rounded-lg border border-slate-100 bg-white shadow-lg">
        <div class=":uno: border-b border-slate-100 p-3">
          <FormKit
            v-model="keyword"
            type="text"
            :placeholder="`搜索${label}`"
            outer-class=":uno: !mb-0"
            input-class=":uno: h-8 rounded-md border border-slate-200 px-2 text-sm"
            @keydown.enter.prevent="handleKeywordEnter"
          />
        </div>
        <div v-if="modelValue.length" class=":uno: flex flex-wrap gap-1 border-b border-slate-100 px-3 py-2">
          <button
            v-for="selectedTag in modelValue"
            :key="selectedTag"
            type="button"
            class=":uno: inline-flex cursor-pointer items-center gap-0.5 rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-700 transition-colors hover:bg-blue-100"
            @click="toggleTag(selectedTag)"
          >
            <span>#{{ selectedTag }}</span>
            <IconClose class=":uno: size-3" />
          </button>
        </div>
        <div class=":uno: max-h-56 overflow-y-auto">
          <button
            v-if="canCreateTag"
            type="button"
            class=":uno: w-full cursor-pointer border-b border-slate-50 px-3 py-2 text-left text-sm text-blue-600 transition-colors hover:bg-blue-50"
            @click="createTagFromKeyword"
          >
            创建标签「{{ keyword?.trim() }}」
          </button>
          <VEntityContainer v-if="searchResults.length">
            <VEntity
              v-for="tag in searchResults"
              :key="tag"
              :is-selected="modelValue.includes(tag)"
              class=":uno: cursor-pointer"
              @click="toggleTag(tag)"
            >
              <template #start>
                <VEntityField>
                  <template #title>
                    <span class=":uno: text-sm">#{{ tag }}</span>
                  </template>
                </VEntityField>
              </template>
            </VEntity>
          </VEntityContainer>
          <div
            v-else-if="!canCreateTag"
            class=":uno: px-3 py-6 text-center text-sm text-slate-400"
          >
            暂无匹配标签
          </div>
        </div>
      </div>
    </template>
  </VDropdown>
</template>