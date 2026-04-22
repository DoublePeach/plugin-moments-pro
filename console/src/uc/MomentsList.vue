<script lang="ts" setup>
import { momentsUcApiClient } from "@/api";
import FilterDropdown from "@/components/FilterDropdown.vue";
import { usePluginShikiScriptLoader } from "@/plugin-supports/shiki/use-plugin-shiki-script-loader";
import { VCard, VLoading, VPageHeader, VPagination } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { useQuery } from "@tanstack/vue-query";
import { useRouteQuery } from "@vueuse/router";
import { computed, provide, ref, watch } from "vue";
import DatePicker from "vue-datepicker-next";
import "vue-datepicker-next/index.css";
import "vue-datepicker-next/locale/zh-cn.es";
import MingcuteMomentsLine from "~icons/mingcute/moment-line";
import MomentEdit from "./MomentEdit.vue";
import MomentItem from "./MomentItem.vue";
import TagFilterDropdown from "./TagFilterDropdown.vue";

const tag = useRouteQuery<string>("tag");

const page = ref(1);
const size = ref(20);
const total = ref(0);
const totalPages = ref(1);
const hasPrevious = ref(false);
const hasNext = ref(false);

const selectedVisible = useRouteQuery<"PUBLIC" | "PRIVATE" | undefined>("visible");
const visibleItems = [
  { label: "公开", value: "PUBLIC" },
  { label: "私有", value: "PRIVATE" },
];

const DEFAULT_SORT = "spec.releaseTime,desc";
const selectedSort = useRouteQuery<string>("sort", DEFAULT_SORT);
const sortItems = [
  { label: "发布时间 ↓", value: "spec.releaseTime,desc" },
  { label: "发布时间 ↑", value: "spec.releaseTime,asc" },
  { label: "创建时间 ↓", value: "metadata.creationTimestamp,desc" },
  { label: "创建时间 ↑", value: "metadata.creationTimestamp,asc" },
];

const keyword = ref("");
const momentsRangeTime = ref<Array<Date>>([]);

const startDate = computed(() => {
  const date = momentsRangeTime.value[0];
  if (!date) {
    return;
  }
  return utils.date.dayjs(date).endOf("day").toISOString();
});
const endDate = computed(() => {
  let endTime: Date = momentsRangeTime.value[1];
  if (!endTime) {
    return;
  }
  return utils.date.dayjs(endTime).endOf("day").toISOString();
});

const {
  data: moments,
  isLoading,
  refetch,
} = useQuery({
  queryKey: [
    "plugin:moments:list",
    page,
    size,
    selectedVisible,
    selectedSort,
    startDate,
    endDate,
    keyword,
    tag,
  ],
  queryFn: async () => {
    const { data } = await momentsUcApiClient.moment.listMyMoments({
      page: page.value,
      size: size.value,
      // @unocss-skip-start
      visible: selectedVisible.value,
      // @unocss-skip-end
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

function updateTagQuery(tagQuery: string) {
  tag.value = tagQuery;
}

provide("tag", {
  tag: tag.value,
  updateTagQuery,
});

watch([tag, momentsRangeTime, selectedVisible, selectedSort], () => {
  page.value = 1;
  size.value = 20;
  refetch();
});

usePluginShikiScriptLoader();
</script>
<template>
  <VPageHeader title="瞬间">
    <template #icon>
      <MingcuteMomentsLine />
    </template>
  </VPageHeader>
  <VCard class=":uno: m-0 flex-1 md:m-4">
    <div class=":uno: mx-auto max-w-4xl px-4 md:px-8">
      <div class=":uno: moments-content my-2 flex flex-col md:my-4 space-y-2">
        <MomentEdit />

        <div class=":uno: moment-header pb-2 pt-8">
          <div class=":uno: flex flex-col justify-between sm:flex-row space-x-2">
            <div class=":uno: left-0 mb-2 mr-2 flex flex-wrap items-center sm:mb-0 gap-2">
              <TagFilterDropdown v-model="tag" :label="'标签'"></TagFilterDropdown>
              <FilterDropdown v-model="selectedVisible" label="可见性" :items="visibleItems" />
              <FilterDropdown v-model="selectedSort" label="排序" :items="sortItems" />
            </div>

            <div class=":uno: right-0 flex !ml-0">
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
        </div>

        <VLoading v-if="isLoading" />

        <Transition v-else appear name="fade">
          <ul
            v-if="moments && moments.length > 0"
            class=":uno: box-border flex flex-col space-y-2"
            role="list"
          >
            <li v-for="moment in moments" :key="moment.moment.metadata.name">
              <MomentItem
                :key="moment.moment.metadata.name"
                :listed-moment="moment"
                @remove="refetch()"
              />
            </li>
          </ul>
          <template v-else>
            <div class=":uno: h-full flex items-center justify-center">
              <span class=":uno: text-gray-500">暂无数据</span>
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
</style>
