<script lang="ts" setup>
import { momentsConsoleApiClient, momentsCoreApiClient } from "@/api";
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
import { useQueryClient } from "@tanstack/vue-query";
import { computed, ref, watch } from "vue";
import LucideMoreHorizontal from "~icons/lucide/more-horizontal";
import MingcutePushpin2Fill from "~icons/mingcute/pin-2-fill";
import MomentEdit from "./MomentEdit.vue";
import MomentPreview from "./MomentPreview.vue";

const props = withDefaults(
  defineProps<{
    listedMoment: ListedMoment;
    editing?: boolean;
    selectable?: boolean;
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

const queryClient = useQueryClient();
const editing = ref(props.editing);
const pinning = ref(false);
const localPinned = ref<boolean | null>(null);
const owner = computed(() => props.listedMoment?.owner);
const momentName = computed(() => props.listedMoment.moment.metadata.name);
const isPinned = computed(() => {
  if (localPinned.value !== null) {
    return localPinned.value;
  }
  return Boolean((props.listedMoment?.moment.spec as { pinned?: boolean })?.pinned);
});

const deleteMoment = () => {
  Dialog.warning({
    title: "确定要删除该瞬间吗？",
    description: "该操作不可逆",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        await momentsCoreApiClient.moment.deleteMoment({
          name: momentName.value,
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
  emit("toggle-select", momentName.value);
}

function invalidateList() {
  queryClient.invalidateQueries({ queryKey: ["plugin:moments:console:list"] });
  queryClient.invalidateQueries({ queryKey: ["plugin:moments:pinned-count"] });
}

watch(
  () => props.listedMoment?.moment.spec,
  () => {
    localPinned.value = null;
  }
);

/** 置顶当前瞬间 */
async function handlePin() {
  if (pinning.value) return;
  pinning.value = true;
  try {
    await momentsConsoleApiClient.pin(momentName.value);
    localPinned.value = true;
    Toast.success("已置顶");
    invalidateList();
  } catch (error) {
    console.error("Failed to pin moment", error);
    Toast.error("置顶失败");
  } finally {
    pinning.value = false;
  }
}

/** 取消置顶当前瞬间 */
async function handleUnpin() {
  if (pinning.value) return;
  pinning.value = true;
  try {
    await momentsConsoleApiClient.unpin(momentName.value);
    localPinned.value = false;
    Toast.success("已取消置顶");
    invalidateList();
  } catch (error) {
    console.error("Failed to unpin moment", error);
    Toast.error("取消置顶失败");
  } finally {
    pinning.value = false;
  }
}
</script>
<template>
  <div>
    <div
      class=":uno: moment-list-card preview relative shrink rounded-lg border border-slate-200 bg-white py-3 transition-colors duration-150 hover:border-slate-300"
      :class="{
        ':uno: border-blue-300 bg-blue-50/30': selected,
      }"
    >
      <div v-if="isPinned" class=":uno: absolute left-0 top-0 h-full w-[2px] rounded-l-lg bg-amber-500"></div>
      <div class=":uno: flex items-start gap-2.5 px-3">
        <label
          v-if="selectable"
          class=":uno: mt-1 flex cursor-pointer select-none items-center justify-center"
          @click.stop
        >
          <input
            type="checkbox"
            class=":uno: h-3.5 w-3.5 cursor-pointer accent-blue-600"
            :checked="selected"
            @change="toggleSelect"
          />
        </label>
        <VAvatar
          :alt="owner?.displayName"
          :src="owner?.avatar"
          size="sm"
          circle
          class=":uno: flex-none"
        ></VAvatar>
        <div class=":uno: min-w-0 flex-1 shrink">
          <div class=":uno: flex items-center justify-between gap-2">
            <div class=":uno: flex min-w-0 flex-wrap items-center gap-1.5">
              <b class=":uno: truncate text-xs text-slate-900 font-medium"> {{ owner?.displayName }} </b>
              <div
                v-if="isPinned"
                v-tooltip="{ content: '已置顶' }"
                class=":uno: inline-flex items-center gap-0.5 rounded bg-amber-50 px-1.5 py-0.5 text-[11px] text-amber-700"
              >
                <MingcutePushpin2Fill class=":uno: size-2.5" />
                <span>置顶</span>
              </div>
              <div
                v-if="listedMoment?.moment.spec.visible == 'PRIVATE'"
                v-tooltip="{ content: '私有访问' }"
              >
                <IconEyeOff class=":uno: text-[11px] text-slate-400" />
              </div>
            </div>

            <div class=":uno: flex shrink-0 items-center">
              <div class=":uno: mr-1.5 cursor-default text-[11px] text-slate-500">
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
                  class=":uno: group flex h-6 w-6 cursor-pointer items-center justify-center rounded transition-colors hover:bg-slate-100"
                >
                  <LucideMoreHorizontal
                    class=":uno: size-3.5 cursor-pointer text-slate-500 group-hover:text-blue-600"
                  />
                </div>
                <template #popper>
                  <VDropdownItem @click="editing = true"> 编辑 </VDropdownItem>
                  <VDropdownItem v-if="!isPinned" :disabled="pinning" @click="handlePin">
                    置顶
                  </VDropdownItem>
                  <VDropdownItem v-else :disabled="pinning" @click="handleUnpin">
                    取消置顶
                  </VDropdownItem>
                  <VDropdownItem type="danger" @click="deleteMoment"> 删除 </VDropdownItem>
                </template>
              </VDropdown>
            </div>
          </div>

          <div class=":uno: mt-2">
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
