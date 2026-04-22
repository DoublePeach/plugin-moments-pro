<script lang="ts" setup>
import { momentsConsoleApiClient } from "@/api";
import FilterDropdown from "@/components/FilterDropdown.vue";
import MomentEdit from "@/components/MomentEdit.vue";
import MomentItem from "@/components/MomentItem.vue";
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
import { useRouteQuery } from "@vueuse/router";
import { computed, provide, ref, watch } from "vue";
import DatePicker from "vue-datepicker-next";
import "vue-datepicker-next/index.css";
import "vue-datepicker-next/locale/zh-cn.es";
import MingcuteCloseFill from "~icons/mingcute/close-fill";
import MingcuteDownloadLine from "~icons/mingcute/download-line";
import MingcuteMomentsLine from "~icons/mingcute/moment-line";

const queryClient = useQueryClient();

const tag = useRouteQuery<string>("tag");
const selectedApprovedStatus = useRouteQuery<string | undefined, boolean | undefined>(
  "approved",
  undefined,
  {
    transform: (value) => {
      return value ? value === "true" : undefined;
    },
  }
);

const page = ref(1);
const size = ref(20);
const total = ref(0);
const totalPages = ref(1);
const hasPrevious = ref(false);
const hasNext = ref(false);

const keyword = ref("");
const momentsRangeTime = ref<Array<Date>>([]);

const DEFAULT_SORT = "spec.releaseTime,desc";
const selectedSort = useRouteQuery<string>("sort", DEFAULT_SORT);
const sortItems = [
  { label: "发布时间 ↓", value: "spec.releaseTime,desc" },
  { label: "发布时间 ↑", value: "spec.releaseTime,asc" },
  { label: "创建时间 ↓", value: "metadata.creationTimestamp,desc" },
  { label: "创建时间 ↑", value: "metadata.creationTimestamp,asc" },
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

// --- batch selection state ---
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
    "plugin:moments:list",
    page,
    size,
    selectedApprovedStatus,
    startDate,
    endDate,
    keyword,
    tag,
    selectedSort,
  ],
  queryFn: async () => {
    const { data } = await momentsConsoleApiClient.moment.listMoments({
      page: page.value,
      size: size.value,
      approved: selectedApprovedStatus.value,
      keyword: keyword.value,
      startDate: startDate.value,
      endDate: endDate.value,
      tag: tag.value,
      sort: selectedSort.value ? [selectedSort.value] : [DEFAULT_SORT],
    });

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

provide("tag", {
  tag: tag.value,
  updateTagQuery,
});

watch([tag, selectedApprovedStatus, momentsRangeTime, selectedSort], () => {
  page.value = 1;
  size.value = 20;
  clearSelection();
  refetch();
});

const handleJumpToFrontDesk = () => {
  window.open("/moments", "_blank");
};

// --- batch actions ---
const refreshAndClear = () => {
  clearSelection();
  queryClient.invalidateQueries({ queryKey: ["plugin:moments:list"] });
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

const batchApprove = async () => {
  if (selectedCount.value === 0) return;
  try {
    const { data } = await momentsConsoleApiClient.approveBatch(Array.from(selected.value));
    Toast.success(`已审核通过 ${data.updated} 条`);
    refreshAndClear();
  } catch (e) {
    console.error(e);
  }
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

// --- export ---
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
  <VCard class=":uno: m-0 flex-1 md:m-4">
    <div class=":uno: mx-auto max-w-4xl px-4 md:px-8">
      <div class=":uno: moments-content my-2 flex flex-col md:my-4 space-y-2">
        <MomentEdit />

        <!-- Filter bar (sticky) -->
        <div
          class=":uno: moment-header sticky top-0 z-10 -mx-4 bg-white/90 px-4 pb-2 pt-6 backdrop-blur md:-mx-8 md:px-8"
        >
          <div class=":uno: flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
            <div class=":uno: flex flex-wrap items-center gap-2">
              <TagFilterDropdown v-model="tag" :label="'标签'"></TagFilterDropdown>
              <FilterDropdown
                v-model="selectedApprovedStatus"
                label="状态"
                :items="[
                  { label: '全部' },
                  { label: '已审核', value: true },
                  { label: '待审核', value: false },
                ]"
              />
              <FilterDropdown v-model="selectedSort" label="排序" :items="sortItems" />
              <button
                class=":uno: inline-flex cursor-pointer select-none items-center border rounded-lg px-3 text-sm leading-9 transition-colors"
                :class="
                  selectable
                    ? ':uno: border-sky-600 bg-sky-50 text-sky-700'
                    : ':uno: text-gray-700 hover:text-black'
                "
                @click="toggleSelectable"
              >
                {{ selectable ? "退出批量" : "批量操作" }}
              </button>
            </div>

            <div class=":uno: flex !ml-0">
              <DatePicker
                v-model:value="momentsRangeTime"
                input-class=":uno: mx-input rounded"
                class=":uno: date-picker range-time max-w-[13rem] cursor-pointer md:max-w-[15rem]"
                range
                :editable="false"
                placeholder="筛选日期范围"
              />
            </div>
          </div>

          <!-- Batch toolbar -->
          <Transition name="fade">
            <div
              v-if="selectable"
              class=":uno: mt-2 flex flex-wrap items-center justify-between gap-2 rounded-md bg-sky-50 px-3 py-2 text-sm"
            >
              <div class=":uno: flex items-center gap-3">
                <label class=":uno: inline-flex cursor-pointer items-center gap-2 select-none">
                  <input
                    type="checkbox"
                    class=":uno: h-4 w-4 cursor-pointer accent-sky-600"
                    :checked="allVisibleSelected"
                    :disabled="visibleNames.length === 0"
                    @change="toggleSelectAllVisible"
                  />
                  <span>全选本页</span>
                </label>
                <span class=":uno: text-gray-600">已选 {{ selectedCount }} 条</span>
              </div>
              <div class=":uno: flex flex-wrap items-center gap-2">
                <VButton
                  size="sm"
                  :disabled="selectedCount === 0"
                  @click="batchApprove"
                >
                  批量审核
                </VButton>
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
                <VButton
                  size="sm"
                  type="danger"
                  :disabled="selectedCount === 0"
                  @click="batchDelete"
                >
                  批量删除
                </VButton>
                <button
                  v-if="selectedCount > 0"
                  v-tooltip="{ content: '清空选择' }"
                  class=":uno: group flex cursor-pointer items-center justify-center rounded-full p-1 hover:bg-gray-200"
                  @click="clearSelection"
                >
                  <MingcuteCloseFill class=":uno: text-sm text-gray-500" />
                </button>
              </div>
            </div>
          </Transition>
        </div>

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
              class=":uno: flex flex-col items-center justify-center py-20 text-gray-500 space-y-3"
            >
              <MingcuteMomentsLine class=":uno: text-5xl text-gray-300" />
              <span>暂无数据</span>
              <span class=":uno: text-xs text-gray-400">
                可以在顶部编辑器直接发布第一条瞬间
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
</template>
<style lang="scss">
.date-picker {
  & input {
    border-radius: 0.375rem;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
