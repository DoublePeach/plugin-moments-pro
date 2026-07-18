<script lang="ts" setup>
import { IconArrowDown, IconClose, VDropdown, VDropdownItem } from "@halo-dev/components";
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    items: {
      label: string;
      value?: string | boolean | number;
    }[];
    label: string;
    modelValue?: string | boolean | number;
    compact?: boolean;
  }>(),
  {
    modelValue: undefined,
    compact: false,
  }
);

const emit = defineEmits<{
  (event: "update:modelValue", modelValue: string | boolean | number | undefined): void;
}>();

const selectedItem = computed(() => {
  return props.items.find((item) => item.value === props.modelValue);
});

function handleSelect(item: { label: string; value?: string | boolean | number }) {
  if (item.value === props.modelValue) {
    emit("update:modelValue", undefined);
    return;
  }
  emit("update:modelValue", item.value);
}

const handleClear = (event: Event) => {
  emit("update:modelValue", undefined);
  event.stopPropagation();
};
</script>

<template>
  <VDropdown>
    <div
      class=":uno: group flex cursor-pointer select-none items-center border rounded-lg text-gray-700 hover:text-black"
      :class="[
        compact
          ? ':uno: moment-filter-control h-7 shrink-0 px-2 text-xs leading-none'
          : ':uno: px-3 text-sm leading-9',
        modelValue !== undefined ? ':uno: font-semibold text-gray-700' : '',
      ]"
    >
      <span v-if="!selectedItem" class=":uno: mr-0.5">
        {{ label }}
      </span>
      <span v-else class=":uno: mr-0.5"> {{ label }}：{{ selectedItem.label }} </span>
      <span class=":uno: text-base">
        <IconArrowDown :class="{ ':uno: group-hover:hidden': modelValue !== undefined }" />
        <IconClose
          v-if="modelValue !== undefined"
          class=":uno: hidden group-hover:block"
          @click="handleClear"
        />
      </span>
    </div>
    <template #popper>
      <VDropdownItem
        v-for="(item, index) in items"
        :key="index"
        :selected="item.value === modelValue"
        @click="handleSelect(item)"
      >
        {{ item.label }}
      </VDropdownItem>
    </template>
  </VDropdown>
</template>
