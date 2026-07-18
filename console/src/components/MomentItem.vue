<script lang="ts" setup>
import { momentsCoreApiClient } from "@/api";
import type { ListedMoment, Moment } from "@/api/generated";
import {
  Dialog,
  IconEyeOff,
  Toast,
  VAvatar,
  VDropdown,
  VDropdownItem,
} from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed, ref } from "vue";
import LucideMoreHorizontal from "~icons/lucide/more-horizontal";
import MingcutePushpin2Fill from "~icons/mingcute/pin-2-fill";
import MomentEdit from "./MomentEdit.vue";
import MomentPreview from "./MomentPreview.vue";

const props = withDefaults(
  defineProps<{
    listedMoment: ListedMoment;
    editing?: boolean;
    /** Whether batch selection mode is active. */
    selectable?: boolean;
    /** Whether this item is currently selected. */
    selected?: boolean;
  }>(),
  {
    editing: false,
    selectable: false,
    selected: false,
  }
);

const emit = defineEmits<{
  (event: "save", moment: Moment): void;
  (event: "update", moment: Moment): void;
  (event: "remove"): void;
  (event: "toggle-select", name: string): void;
}>();

const editing = ref(props.editing);
const owner = computed(() => props.listedMoment?.owner);
const isPinned = computed(() =>
  Boolean((props.listedMoment?.moment.spec as { pinned?: boolean })?.pinned)
);

const deleteMoment = () => {
  Dialog.warning({
    title: "确定要删除该瞬间吗？",
    description: "该操作不可逆",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        await momentsCoreApiClient.moment.deleteMoment({
          name: props.listedMoment.moment.metadata.name,
        });

        Toast.success("删除成功");
        emit("remove");
      } catch (error) {
        console.error("Failed to delete moment", error);
      }
    },
  });
};

const onUpdated = () => {
  editing.value = false;
};

function toggleSelect() {
  emit("toggle-select", props.listedMoment.moment.metadata.name);
}
</script>
<template>
  <div>
    <div
      class=":uno: moment-list-card preview relative shrink rounded-xl border border-slate-200 bg-white py-5 transition-all duration-200 hover:border-slate-300 hover:shadow-sm"
      :class="{
        ':uno: border-blue-300 bg-blue-50/30 shadow-sm': selected,
      }"
    >
      <div v-if="isPinned" class=":uno: absolute left-0 top-0 h-full w-[3px] rounded-l-xl bg-amber-500"></div>
      <div class=":uno: flex items-start gap-3 px-4">
        <label
          v-if="selectable"
          class=":uno: mt-2 flex cursor-pointer select-none items-center justify-center"
          @click.stop
        >
          <input
            type="checkbox"
            class=":uno: h-4 w-4 cursor-pointer accent-blue-600"
            :checked="selected"
            @change="toggleSelect"
          />
        </label>
        <VAvatar
          :alt="owner?.displayName"
          :src="owner?.avatar"
          size="md"
          circle
          class=":uno: flex-none"
        ></VAvatar>
        <div class=":uno: min-w-0 flex-1 shrink">
          <div class=":uno: flex items-center justify-between gap-2">
            <div class=":uno: flex min-w-0 flex-wrap items-center gap-2">
              <b class=":uno: truncate text-slate-900"> {{ owner?.displayName }} </b>
              <div
                v-if="isPinned"
                v-tooltip="{ content: '已置顶' }"
                class=":uno: inline-flex items-center gap-0.5 rounded-full bg-amber-50 px-2 py-0.5 text-xs text-amber-700"
              >
                <MingcutePushpin2Fill class=":uno: size-3" />
                <span>置顶</span>
              </div>
              <div
                v-if="listedMoment?.moment.spec.visible == 'PRIVATE'"
                v-tooltip="{
                  content: '私有访问',
                }"
              >
                <IconEyeOff class=":uno: text-xs text-slate-400" />
              </div>
            </div>

            <div class=":uno: flex shrink-0 items-center">
              <div class=":uno: mr-2 cursor-default text-xs text-slate-500">
                <span
                  v-tooltip="{
                    content: utils.date.format(listedMoment?.moment.spec.releaseTime),
                  }"
                >
                  {{ utils.date.timeAgo(listedMoment?.moment.spec.releaseTime) }}
                </span>
              </div>
              <VDropdown v-permission="['plugin:moments:manage']" compute-transform-origin>
                <div
                  class=":uno: group flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg transition-colors hover:bg-slate-100"
                >
                  <LucideMoreHorizontal
                    class=":uno: size-4 cursor-pointer text-slate-500 group-hover:text-blue-600"
                  />
                </div>
                <template #popper>
                  <VDropdownItem @click="editing = true"> 编辑 </VDropdownItem>
                  <VDropdownItem type="danger" @click="deleteMoment"> 删除 </VDropdownItem>
                </template>
              </VDropdown>
            </div>
          </div>

          <div class=":uno: mt-3">
            <MomentEdit
              v-if="editing"
              :moment="listedMoment?.moment"
              @update="onUpdated"
              @cancel="editing = false"
            ></MomentEdit>
            <MomentPreview
              v-else
              :uc="false"
              :moment="listedMoment"
              @switch-edit-mode="editing = true"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
