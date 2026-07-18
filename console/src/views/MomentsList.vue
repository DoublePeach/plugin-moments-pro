<script lang="ts" setup>
import { momentsConsoleApiClient } from "@/api";
import FilterDropdown from "@/components/FilterDropdown.vue";
import MomentEdit from "@/components/MomentEdit.vue";
import MomentItem from "@/components/MomentItem.vue";
import PinnedMomentsModal from "@/components/PinnedMomentsModal.vue";
import TagFilterDropdown from "@/components/TagFilterDropdown.vue";
import { usePluginShikiScriptLoader } from "@/plugin-supports/shiki/use-plugin-shiki-script-loader";
import {
  Dialog,
  IconExternalLinkLine,
  Toast,
  VButton,
  VCard,
  VLoading,
  VPageHeader,
  VPagination,
} from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { useQuery, useQueryClient } from "@tanstack/vue-query";
import { refDebounced } from "@vueuse/core";
import { useRouteQuery } from "@vueuse/router";
import { computed, provide, ref, watch } from "vue";
import { useRouter } from "vue-router";
import DatePicker from "vue-datepicker-next";
import "vue-datepicker-next/index.css";
import "vue-datepicker-next/locale/zh-cn.es";
import MingcuteCloseFill from "~icons/mingcute/close-fill";
import MingcuteDownloadLine from "~icons/mingcute/download-line";
import MingcuteMomentsLine from "~icons/mingcute/moment-line";
import MingcutePin2Fill from "~icons/mingcute/pin-2-fill";
import MingcuteTag2Line from "~icons/mingcute/tag-2-line";

const router = useRouter();
const queryClient = useQueryClient();

const tag = useRouteQuery<string | undefined>("tag");
const selectedVisible = useRouteQuery<"PUBLIC" | "PRIVATE" | undefined>("visible");
const ownerName = useRouteQuery<string | undefined>("ownerName");
const debouncedOwnerName = refDebounced(ownerName, 300);

const page = ref(1);
const size = ref(20);
const total = ref(0);
const totalPages = ref(1);
const hasPrevious = ref(false);
const hasNext = ref(false);

const momentsRangeTime = ref<Array<Date>>([]);

const DEFAULT_SORT = "spec.releaseTime,desc";
const selectedSort = useRouteQuery<string>("sort", DEFAULT_SORT);
const sortItems = [
  { label: "发布时间 ↓", value: "spec.releaseTime,desc" },
  { label: "发布时间 ↑", value: "spec.releaseTime,asc" },
  { label: "创建时间 ↓", value: "metadata.creationTimestamp,desc" },
  { label: "创建时间 ↑", value: "metadata.creationTimestamp,asc" },
];
const visibleItems = [
  { label: "公开", value: "PUBLIC" },
  { label: "私有", value: "PRIVATE" },
];

const startDate = computed(() => {
  const date: Date = momentsRangeTime.value[0];
  if (!date) return;
  return utils.date.dayjs(date).startOf("day").toISOString();
});
const endDate = computed(() => {
  const endTime: Date = momentsRangeTime.value[1];
  if (!endTime) return;
  return utils.date.dayjs(endTime).endOf("day").toISOString();
});

const selectable = ref(false);
const selected = ref<Set<string>>(new Set());
const selectedCount = computed(() => selected.value.size);

function toggleSelectable() {
  selectable.value = !selectable.value;
  if (!selectable.value) {
    selected.value = new Set();
  }
}

function toggleSelect(name: string) {
  const next = new Set(selected.value);
  if (next.has(name)) {
    next.delete(name);
  } else {
    next.add(name);
  }
  selected.value = next;
}

function isSelected(name: string) {
  return selected.value.has(name);
}

function clearSelection() {
  selected.value = new Set();
}

const {
  data: moments,
  isLoading,
  isFetching,
  refetch,
} = useQuery({
  queryKey: [
    "plugin:moments:console:list",
    page,
    size,
    selectedVisible,
    debouncedOwnerName,
    startDate,
    endDate,
    tag,
    selectedSort,
  ],
  queryFn: async () => {
    const { data } = await momentsConsoleApiClient.moment.listMoments(
      {
        page: page.value,
        size: size.value,
        ownerName: debouncedOwnerName.value || undefined,
        visible: selectedVisible.value,
        startDate: startDate.value,
        endDate: endDate.value,
        tag: tag.value,
        sort: selectedSort.value ? [selectedSort.value] : [DEFAULT_SORT],
      },
      { params: { pinSort: false } }
    );

    total.value = data.total;
    totalPages.value = data.totalPages;
    hasNext.value = data.hasNext;
    hasPrevious.value = data.hasPrevious;
    return data.items;
  },
  refetchInterval: (data) => {
    const hasDeletingData = data?.some((moment) => {
      return !!moment.moment.metadata.deletionTimestamp;
    });
    return hasDeletingData ? 1000 : false;
  },
  refetchOnWindowFocus: false,
});

const visibleNames = computed(() =>
  (moments.value ?? []).map((m) => m.moment.metadata.name)
);
const allVisibleSelected = computed(() => {
  if (visibleNames.value.length === 0) return false;
  return visibleNames.value.every((n) => selected.value.has(n));
});

function toggleSelectAllVisible() {
  const next = new Set(selected.value);
  if (allVisibleSelected.value) {
    visibleNames.value.forEach((n) => next.delete(n));
  } else {
    visibleNames.value.forEach((n) => next.add(n));
  }
  selected.value = next;
}

function updateTagQuery(tagQuery: string) {
  tag.value = tagQuery;
}

function clearTagFilter() {
  tag.value = undefined;
}

provide("tag", {
  tag: tag.value,
  updateTagQuery,
});

watch(
  [tag, selectedVisible, debouncedOwnerName, momentsRangeTime, selectedSort],
  () => {
    page.value = 1;
    size.value = 20;
    clearSelection();
    refetch();
  }
);

const handleJumpToFrontDesk = () => {
  window.open("/moments", "_blank");
};

const handleOpenTagManage = () => {
  router.push({ path: "/moments/tags" });
};

const pinnedModalVisible = ref(false);

const { data: pinnedCount } = useQuery({
  queryKey: ["plugin:moments:pinned-count"],
  queryFn: async () => {
    const { data } = await momentsConsoleApiClient.moment.listMoments({
      page: 1,
      size: 1,
      fieldSelector: ["spec.pinned=true"],
    });
    return data.total;
  },
  refetchOnWindowFocus: false,
});

function openPinnedModal() {
  pinnedModalVisible.value = true;
}

function onPinnedChanged() {
  queryClient.invalidateQueries({ queryKey: ["plugin:moments:console:list"] });
  queryClient.invalidateQueries({ queryKey: ["plugin:moments:pinned-count"] });
}

const refreshAndClear = () => {
  clearSelection();
  queryClient.invalidateQueries({ queryKey: ["plugin:moments:console:list"] });
};

const batchDelete = () => {
  if (selectedCount.value === 0) return;
  Dialog.warning({
    title: `确定要删除这 ${selectedCount.value} 条瞬间吗？`,
    description: "该操作不可逆",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        const { data } = await momentsConsoleApiClient.deleteBatch(
          Array.from(selected.value)
        );
        Toast.success(`已删除 ${data.deleted} 条`);
        refreshAndClear();
      } catch (e) {
        console.error(e);
      }
    },
  });
};

const batchSetVisible = async (visible: "PUBLIC" | "PRIVATE") => {
  if (selectedCount.value === 0) return;
  try {
    const { data } = await momentsConsoleApiClient.setVisibleBatch(
      Array.from(selected.value),
      visible
    );
    Toast.success(`已更新 ${data.updated} 条为${visible === "PUBLIC" ? "公开" : "私有"}`);
    refreshAndClear();
  } catch (e) {
    console.error(e);
  }
};

const exportJson = () => {
  if (!moments.value || moments.value.length === 0) {
    Toast.warning("当前没有可导出的数据");
    return;
  }
  const data = JSON.stringify(moments.value, null, 2);
  const blob = new Blob([data], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `moments-page-${page.value}-${new Date()
    .toISOString()
    .slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
  Toast.success("已导出当前页为 JSON");
};

usePluginShikiScriptLoader();
</script>
<template>
  <VPageHeader title="瞬间">
    <template #icon>
      <MingcuteMomentsLine />
    </template>
    <template #actions>
      <VButton @click="handleOpenTagManage">
        <template #icon>
          <MingcuteTag2Line class=":uno: size-full" />
        </template>
        标签管理
      </VButton>
      <VButton @click="openPinnedModal">
        <template #icon>
          <MingcutePin2Fill class=":uno: size-full" />
        </template>
        置顶管理
        <span
          v-if="pinnedCount && pinnedCount > 0"
          class=":uno: ml-1 inline-flex min-w-[1.25rem] items-center justify-center rounded-full bg-amber-100 px-1.5 text-xs font-semibold text-amber-700"
        >
          {{ pinnedCount }}
        </span>
      </VButton>
      <VButton @click="exportJson">
        <template #icon>
          <MingcuteDownloadLine class=":uno: size-full" />
        </template>
        导出本页
      </VButton>
      <VButton @click="handleJumpToFrontDesk">
        <template #icon>
          <IconExternalLinkLine class=":uno: size-full" />
        </template>
        跳转到前台
      </VButton>
    </template>
  </VPageHeader>
  <VCard class=":uno: moments-page-card m-0 flex-1 md:m-3">
    <div class=":uno: moments-content mx-auto w-full max-w-5xl px-3 md:px-4">
      <div class=":uno: my-1 flex flex-col space-y-1 md:my-1.5">
        <section aria-label="发布瞬间">
          <MomentEdit />
        </section>

        <section
          class=":uno: moment-header sticky top-0 z-10 -mx-3 rounded border border-slate-200 bg-white px-1.5 py-0 md:-mx-4 md:px-2"
          aria-label="筛选与批量操作"
        >
          <div class=":uno: moment-filter-bar flex items-center gap-1 overflow-x-auto">
            <TagFilterDropdown v-model="tag" label="标签" compact />
            <FilterDropdown v-model="selectedVisible" label="可见性" :items="visibleItems" compact />
            <FilterDropdown v-model="selectedSort" label="排序" :items="sortItems" compact />
            <button
              type="button"
              class=":uno: moment-filter-control inline-flex h-7 shrink-0 cursor-pointer select-none items-center rounded border px-2 text-xs transition-colors"
              :class="
                selectable
                  ? ':uno: border-blue-500 bg-blue-50 text-blue-700'
                  : ':uno: border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50'
              "
              @click="toggleSelectable"
            >
              {{ selectable ? "退出批量" : "批量" }}
            </button>
            <div class=":uno: mx-1 hidden h-4 w-px shrink-0 bg-slate-200 sm:block"></div>
            <FormKit
              v-model="ownerName"
              type="text"
              placeholder="作者"
              outer-class=":uno: !mb-0 shrink-0"
              input-class=":uno: moment-filter-input h-7 w-[6.5rem] rounded border border-slate-200 px-2 text-xs"
            />
            <DatePicker
              v-model:value="momentsRangeTime"
              input-class=":uno: mx-input rounded moment-filter-input"
              class=":uno: date-picker range-time shrink-0 cursor-pointer"
              range
              :editable="false"
              placeholder="日期范围"
            />
          </div>

          <Transition name="fade">
            <div
              v-if="tag"
              class=":uno: mt-1 flex items-center justify-between rounded bg-blue-50 px-2 py-0.5 text-xs text-blue-800"
            >
              <span>当前标签：{{ tag }}</span>
              <button
                type="button"
                class=":uno: cursor-pointer text-[11px] text-blue-600 hover:text-blue-800"
                @click="clearTagFilter"
              >
                清除
              </button>
            </div>
          </Transition>

          <Transition name="fade">
            <div
              v-if="selectable"
              class=":uno: mt-1 flex flex-wrap items-center justify-between gap-1 rounded border border-slate-200 bg-slate-50 px-2 py-1 text-xs"
            >
              <div class=":uno: flex items-center gap-3">
                <label class=":uno: inline-flex cursor-pointer items-center gap-2 select-none">
                  <input
                    type="checkbox"
                    class=":uno: h-4 w-4 cursor-pointer accent-blue-600"
                    :checked="allVisibleSelected"
                    :disabled="visibleNames.length === 0"
                    @change="toggleSelectAllVisible"
                  />
                  <span>全选本页</span>
                </label>
                <span class=":uno: text-slate-600">已选 {{ selectedCount }} 条</span>
              </div>
              <div class=":uno: flex flex-wrap items-center gap-2">
                <VButton
                  size="sm"
                  :disabled="selectedCount === 0"
                  @click="batchSetVisible('PUBLIC')"
                >
                  设为公开
                </VButton>
                <VButton
                  size="sm"
                  :disabled="selectedCount === 0"
                  @click="batchSetVisible('PRIVATE')"
                >
                  设为私有
                </VButton>
                <VButton size="sm" type="danger" :disabled="selectedCount === 0" @click="batchDelete">
                  批量删除
                </VButton>
                <button
                  v-if="selectedCount > 0"
                  v-tooltip="{ content: '清空选择' }"
                  type="button"
                  class=":uno: group flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg transition-colors hover:bg-slate-200"
                  @click="clearSelection"
                >
                  <MingcuteCloseFill class=":uno: text-sm text-slate-500" />
                </button>
              </div>
            </div>
          </Transition>
        </section>

        <VLoading v-if="isLoading" />

        <Transition v-else appear name="fade">
          <ul
            v-if="moments && moments.length > 0"
            class=":uno: box-border flex flex-col space-y-2"
            :class="{ ':uno: opacity-60 pointer-events-none': isFetching }"
            role="list"
          >
            <li v-for="moment in moments" :key="moment.moment.metadata.name">
              <MomentItem
                :key="moment.moment.metadata.name"
                :listed-moment="moment"
                :selectable="selectable"
                :selected="isSelected(moment.moment.metadata.name)"
                @remove="refetch()"
                @toggle-select="toggleSelect"
              />
            </li>
          </ul>
          <template v-else>
            <div
              class=":uno: flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 bg-white py-12 text-slate-500 space-y-2"
            >
              <MingcuteMomentsLine class=":uno: text-4xl text-slate-300" />
              <span class=":uno: text-sm text-slate-600">暂无瞬间</span>
              <span class=":uno: text-xs text-slate-400">
                可在顶部编辑器发布第一条瞬间
              </span>
            </div>
          </template>
        </Transition>

        <div v-if="hasPrevious || hasNext" class=":uno: my-5 flex justify-center">
          <VPagination
            v-model:page="page"
            v-model:size="size"
            class=":uno: !bg-transparent"
            :total="total"
            :size-options="[20, 30, 50, 100]"
          />
        </div>
      </div>
    </div>
  </VCard>
  <PinnedMomentsModal v-model:visible="pinnedModalVisible" @change="onPinnedChanged" />
</template>
<style lang="scss">
.date-picker {
  & input {
    border-radius: 0.5rem;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>