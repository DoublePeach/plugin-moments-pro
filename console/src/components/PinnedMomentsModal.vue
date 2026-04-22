<script setup lang="ts">
import { momentsConsoleApiClient } from "@/api";
import type { ListedMoment } from "@/api/generated";
import {
  Dialog,
  Toast,
  VAvatar,
  VButton,
  VEmpty,
  VLoading,
  VModal,
} from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { useQueryClient } from "@tanstack/vue-query";
import { ref, watch } from "vue";
import MingcuteArrowDownLine from "~icons/mingcute/arrow-down-line";
import MingcuteArrowUpLine from "~icons/mingcute/arrow-up-line";
import MingcuteClose from "~icons/mingcute/close-line";
import MingcutePin2Fill from "~icons/mingcute/pin-2-fill";

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (event: "update:visible", value: boolean): void;
  (event: "change"): void;
}>();

const queryClient = useQueryClient();

const modalRef = ref<InstanceType<typeof VModal> | null>(null);
const list = ref<ListedMoment[]>([]);
const loading = ref(false);
const saving = ref(false);
// The ordered list is considered dirty when the user has changed any order or removed items.
const dirty = ref(false);

async function loadPinned() {
  loading.value = true;
  try {
    const { data } = await momentsConsoleApiClient.moment.listMoments({
      page: 1,
      size: 200,
      // @ts-expect-error - pinned is a new query param not yet regenerated in the API client.
      pinned: true,
      sort: ["spec.pinOrder,desc"],
    });
    list.value = [...data.items];
    dirty.value = false;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      loadPinned();
    }
  },
  { immediate: true }
);

function moveUp(index: number) {
  if (index <= 0) return;
  const next = [...list.value];
  [next[index - 1], next[index]] = [next[index], next[index - 1]];
  list.value = next;
  dirty.value = true;
}

function moveDown(index: number) {
  if (index >= list.value.length - 1) return;
  const next = [...list.value];
  [next[index], next[index + 1]] = [next[index + 1], next[index]];
  list.value = next;
  dirty.value = true;
}

function moveTop(index: number) {
  if (index <= 0) return;
  const next = [...list.value];
  const [item] = next.splice(index, 1);
  next.unshift(item);
  list.value = next;
  dirty.value = true;
}

function moveBottom(index: number) {
  if (index >= list.value.length - 1) return;
  const next = [...list.value];
  const [item] = next.splice(index, 1);
  next.push(item);
  list.value = next;
  dirty.value = true;
}

async function handleUnpin(item: ListedMoment) {
  Dialog.warning({
    title: "确定取消该瞬间的置顶吗？",
    onConfirm: async () => {
      try {
        await momentsConsoleApiClient.unpin(item.moment.metadata.name);
        Toast.success("已取消置顶");
        list.value = list.value.filter(
          (m) => m.moment.metadata.name !== item.moment.metadata.name
        );
        queryClient.invalidateQueries({ queryKey: ["plugin:moments:list"] });
        emit("change");
      } catch (e) {
        console.error(e);
      }
    },
  });
}

async function handleSave() {
  if (!dirty.value) {
    modalRef.value?.close();
    return;
  }
  saving.value = true;
  try {
    await momentsConsoleApiClient.reorderPinned(
      list.value.map((m) => m.moment.metadata.name)
    );
    Toast.success("置顶顺序已保存");
    queryClient.invalidateQueries({ queryKey: ["plugin:moments:list"] });
    emit("change");
    modalRef.value?.close();
  } catch (e) {
    console.error(e);
  } finally {
    saving.value = false;
  }
}

function handleClose() {
  emit("update:visible", false);
}

function briefContent(item: ListedMoment): string {
  const raw = item.moment.spec.content?.raw || item.moment.spec.content?.html || "";
  const text = raw.replace(/<[^>]+>/g, "").trim();
  return text.length > 80 ? text.slice(0, 80) + "…" : text || "(无文字内容)";
}
</script>

<template>
  <VModal
    ref="modalRef"
    :visible="visible"
    title="置顶管理"
    :width="640"
    height="70vh"
    :layer-closable="true"
    @close="handleClose"
  >
    <template #center>
      <span class=":uno: text-xs text-gray-500">拖动上/下/置顶/置底按钮来调整顺序</span>
    </template>

    <div class=":uno: flex flex-col gap-2">
      <VLoading v-if="loading" />
      <VEmpty
        v-else-if="list.length === 0"
        title="暂无置顶瞬间"
        message="你可以在列表中点击「置顶」来把重要的瞬间固定到最上面。"
      />
      <ul v-else class=":uno: flex flex-col divide-y divide-gray-100 rounded border">
        <li
          v-for="(item, index) in list"
          :key="item.moment.metadata.name"
          class=":uno: flex items-center gap-3 bg-white px-3 py-2.5 transition-colors hover:bg-sky-50/40"
        >
          <div
            class=":uno: flex h-6 w-6 flex-none items-center justify-center rounded-full bg-amber-100 text-xs font-semibold text-amber-700"
          >
            {{ index + 1 }}
          </div>
          <VAvatar
            :alt="item.owner?.displayName"
            :src="item.owner?.avatar"
            size="sm"
            circle
            class=":uno: flex-none"
          />
          <div class=":uno: flex min-w-0 flex-1 flex-col">
            <div class=":uno: flex items-center gap-2">
              <span class=":uno: truncate text-sm font-medium">
                {{ item.owner?.displayName }}
              </span>
              <span class=":uno: text-xs text-gray-400">
                {{ utils.date.format(item.moment.spec.releaseTime, "YYYY-MM-DD") }}
              </span>
            </div>
            <span class=":uno: mt-0.5 truncate text-xs text-gray-500">
              {{ briefContent(item) }}
            </span>
          </div>

          <div class=":uno: flex flex-none items-center gap-0.5">
            <button
              v-tooltip="{ content: '置顶' }"
              class=":uno: group flex h-7 w-7 cursor-pointer items-center justify-center rounded hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-30"
              :disabled="index === 0"
              @click="moveTop(index)"
            >
              <MingcutePin2Fill class=":uno: text-sm text-gray-500 group-hover:text-sky-600" />
            </button>
            <button
              v-tooltip="{ content: '上移' }"
              class=":uno: group flex h-7 w-7 cursor-pointer items-center justify-center rounded hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-30"
              :disabled="index === 0"
              @click="moveUp(index)"
            >
              <MingcuteArrowUpLine class=":uno: text-sm text-gray-500 group-hover:text-sky-600" />
            </button>
            <button
              v-tooltip="{ content: '下移' }"
              class=":uno: group flex h-7 w-7 cursor-pointer items-center justify-center rounded hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-30"
              :disabled="index === list.length - 1"
              @click="moveDown(index)"
            >
              <MingcuteArrowDownLine class=":uno: text-sm text-gray-500 group-hover:text-sky-600" />
            </button>
            <button
              v-tooltip="{ content: '置底' }"
              class=":uno: group flex h-7 w-7 cursor-pointer items-center justify-center rounded hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-30"
              :disabled="index === list.length - 1"
              @click="moveBottom(index)"
            >
              <MingcuteArrowDownLine
                class=":uno: text-sm text-gray-500 scale-x-125 group-hover:text-sky-600"
              />
            </button>
            <button
              v-tooltip="{ content: '取消置顶' }"
              class=":uno: group ml-1 flex h-7 w-7 cursor-pointer items-center justify-center rounded hover:bg-red-100"
              @click="handleUnpin(item)"
            >
              <MingcuteClose class=":uno: text-sm text-gray-500 group-hover:text-red-600" />
            </button>
          </div>
        </li>
      </ul>
    </div>

    <template #footer>
      <VButton type="secondary" @click="modalRef?.close()">取消</VButton>
      <VButton
        type="primary"
        :loading="saving"
        :disabled="!dirty || loading"
        @click="handleSave"
      >
        {{ dirty ? "保存顺序" : "已保存" }}
      </VButton>
    </template>
  </VModal>
</template>
