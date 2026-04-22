<script lang="ts" setup>
import { momentsConsoleApiClient, momentsCoreApiClient } from "@/api";
import type { Moment, MomentMedia, MomentMediaTypeEnum } from "@/api/generated";
import MediaCard from "@/components/MediaCard.vue";
import { useConsoleTagQueryFetch } from "@/composables/use-tag";
import { IconEye, IconEyeOff, Toast, VButton, VLoading } from "@halo-dev/components";
import type { AttachmentLike } from "@halo-dev/ui-shared";
import { useQueryClient } from "@tanstack/vue-query";
import { cloneDeep } from "es-toolkit";
import { computed, defineAsyncComponent, onMounted, ref, toRaw } from "vue";
import DatePicker from "vue-datepicker-next";
import "vue-datepicker-next/index.css";
import "vue-datepicker-next/locale/zh-cn.es";
import SendMoment from "~icons/ic/sharp-send";
import MingcuteHeartFill from "~icons/mingcute/heart-fill";
import TablerPhoto from "~icons/tabler/photo";

const TextEditor = defineAsyncComponent({
  loader: () => import("@/components/TextEditor.vue"),
  loadingComponent: VLoading,
});

const props = withDefaults(
  defineProps<{
    moment?: Moment;
  }>(),
  {
    moment: undefined,
  }
);

const emit = defineEmits<{
  (event: "update"): void;
  (event: "cancel"): void;
}>();

const queryClient = useQueryClient();

const initMoment: Moment = {
  spec: {
    content: {
      raw: "",
      html: "",
      medium: [],
    },
    releaseTime: new Date().toISOString(),
    owner: "",
    // @unocss-skip-start
    visible: "PUBLIC",
    // @unocss-skip-end
    tags: [],
    approved: true,
  },
  metadata: {
    generateName: "moment-",
    name: "",
  },
  kind: "Moment",
  apiVersion: "moment.halo.run/v1alpha1",
};

onMounted(async () => {
  if (props.moment) {
    formState.value = cloneDeep(props.moment);
    // Preload upvote count for display / editing in update mode.
    try {
      const counter = await momentsConsoleApiClient.getCounter(
        props.moment.metadata.name
      );
      upvoteInitial.value = counter?.upvote ?? 0;
      upvoteEdit.value = upvoteInitial.value;
    } catch (e) {
      console.error("Failed to fetch moment counter", e);
    }
  }
});

const formState = ref<Moment>(cloneDeep(initMoment));
const saving = ref<boolean>(false);
const attachmentSelectorModal = ref(false);
const isUpdateMode = computed(() => !!formState.value.metadata.creationTimestamp);
const isEditorEmpty = ref<boolean>(true);
const upvoteInitial = ref<number>(0);
const upvoteEdit = ref<number>(0);
const upvoteChanged = computed(() => upvoteEdit.value !== upvoteInitial.value);
const handlerCreateOrUpdateMoment = async () => {
  if (saveDisable.value) {
    return;
  }
  try {
    saving.value = true;
    queryEditorTags();
    if (isUpdateMode.value) {
      handleUpdate();
    } else {
      handleSave(formState.value);
      handleReset();
    }
  } catch (error) {
    console.error(error);
  } finally {
    saving.value = false;
  }
};

const handleSave = async (moment: Moment) => {
  if (!moment.spec.releaseTime) {
    moment.spec.releaseTime = new Date().toISOString();
  }
  moment.spec.approved = true;

  await momentsConsoleApiClient.moment.createMoment({
    moment: moment,
  });

  queryClient.invalidateQueries({ queryKey: ["plugin:moments:list"] });

  Toast.success("发布成功");
};

const handleUpdate = async () => {
  await momentsCoreApiClient.moment.patchMoment({
    name: formState.value.metadata.name,
    jsonPatchInner: [
      {
        op: "add",
        path: "/spec/tags",
        value: formState.value.spec.tags || [],
      },
      {
        op: "add",
        path: "/spec/content",
        value: formState.value.spec.content,
      },
      {
        op: "add",
        path: "/spec/visible",
        value: formState.value.spec.visible || false,
      },
      {
        op: "add",
        path: "/spec/releaseTime",
        value: formState.value.spec.releaseTime || new Date().toISOString(),
      },
    ],
  });

  // Persist upvote count change separately via Counter endpoint.
  if (upvoteChanged.value) {
    const nextUpvote = Number.isFinite(upvoteEdit.value)
      ? Math.max(0, Math.floor(upvoteEdit.value))
      : 0;
    try {
      await momentsConsoleApiClient.setUpvote(
        formState.value.metadata.name,
        nextUpvote
      );
      upvoteInitial.value = nextUpvote;
      upvoteEdit.value = nextUpvote;
    } catch (e) {
      console.error("Failed to update upvote count", e);
      Toast.error("点赞数更新失败");
    }
  }

  emit("update");

  queryClient.invalidateQueries({ queryKey: ["plugin:moments:list"] });

  Toast.success("发布成功");
};

const parse = new DOMParser();
const queryEditorTags = function () {
  let tags: Set<string> = new Set();
  let document: Document = parse.parseFromString(formState.value.spec.content.raw!, "text/html");
  let nodeList: NodeList = document.querySelectorAll("a.tag");
  if (nodeList) {
    for (let tagNode of nodeList) {
      if (tagNode.textContent) {
        tags.add(tagNode.textContent);
      }
    }
  }
  formState.value.spec.tags = Array.from(tags);
};

const handleReset = () => {
  formState.value = toRaw(cloneDeep(initMoment));
  isEditorEmpty.value = true;
};

const supportImageTypes: string[] = [
  "image/apng",
  "image/avif",
  "image/bmp",
  "image/gif",
  "image/x-icon",
  "image/jpg",
  "image/jpeg",
  "image/png",
  "image/svg+xml",
  "image/tiff",
  "image/webp",
];

const supportVideoTypes: string[] = ["video/*"];

const supportAudioTypes: string[] = ["audio/*"];

const accepts = [...supportImageTypes, ...supportVideoTypes, ...supportAudioTypes];

const mediumWhitelist: Map<string, MomentMediaTypeEnum> = new Map([
  ["image", "PHOTO"],
  ["video", "VIDEO"],
  ["audio", "AUDIO"],
]);

const onAttachmentsSelect = async (attachments: AttachmentLike[]) => {
  const medias: {
    url: string;
    cover?: string;
    displayName?: string;
    type?: string;
  }[] = attachments
    .map((attachment) => {
      if (typeof attachment === "string") {
        return {
          url: attachment,
          cover: attachment,
        };
      }
      if ("url" in attachment) {
        return {
          url: attachment.url,
          cover: attachment.url,
        };
      }
      if ("spec" in attachment) {
        return {
          url: attachment.status?.permalink,
          cover: attachment.status?.permalink,
          displayName: attachment.spec.displayName,
          type: attachment.spec.mediaType,
        };
      }
    })
    .filter(Boolean) as {
    url: string;
    cover?: string;
    displayName?: string;
    type?: string;
  }[];
  if (!formState.value.spec.content.medium) {
    formState.value.spec.content.medium = [];
  }
  medias.forEach((media) => {
    if (!addMediumVerify(media)) {
      return false;
    }
    if (!media.type) {
      return false;
    }
    let fileType = media.type.split("/")[0];
    formState.value.spec.content.medium?.push({
      type: mediumWhitelist.get(fileType),
      url: media.url,
      originType: media.type,
    } as MomentMedia);
  });
};

const saveDisabledReason = computed<string | null>(() => {
  const medium = formState.value.spec.content.medium;
  const hasValidMedium = !!medium && medium.length > 0 && medium.length <= 9;
  const hasContent = !isEditorEmpty.value;

  if (!hasValidMedium && !hasContent) {
    if (medium && medium.length > 9) {
      return `附件数量超过上限（${medium.length}/9）`;
    }
    if (!isUpdateMode.value) {
      return "请先填写内容或添加附件";
    }
  }

  if (isUpdateMode.value) {
    const oldVisible = props.moment?.spec.visible;
    const oldReleaseTime = props.moment?.spec.releaseTime;
    const contentChanged = hasContent || hasValidMedium;
    const visibleChanged = oldVisible !== formState.value.spec.visible;
    const releaseTimeChanged = oldReleaseTime !== formState.value.spec.releaseTime;
    if (
      !contentChanged
      && !visibleChanged
      && !releaseTimeChanged
      && !upvoteChanged.value
    ) {
      return "未检测到任何修改";
    }
    return null;
  }

  if (!hasContent && !hasValidMedium) {
    return "请先填写内容或添加附件";
  }

  return null;
});

const saveDisable = computed(() => saveDisabledReason.value !== null);

// DatePicker 只负责日期选择，后端仍存完整 Instant；我们固定把时间设置为 12:00:00
// 这样既避免时区跨天问题，展示也能稳定回显成用户选择的那一天。
const releaseTimeDate = computed<Date | null>({
  get: () => {
    if (!formState.value.spec.releaseTime) return null;
    const d = new Date(formState.value.spec.releaseTime);
    d.setHours(0, 0, 0, 0);
    return d;
  },
  set: (val) => {
    const target = val ? new Date(val) : new Date();
    target.setHours(12, 0, 0, 0);
    formState.value.spec.releaseTime = target.toISOString();
  },
});

const releaseTimeShortcuts = [
  {
    text: "今天",
    onClick: () => {
      const d = new Date();
      d.setHours(0, 0, 0, 0);
      return d;
    },
  },
  {
    text: "昨天",
    onClick: () => {
      const d = new Date();
      d.setDate(d.getDate() - 1);
      d.setHours(0, 0, 0, 0);
      return d;
    },
  },
  {
    text: "明天",
    onClick: () => {
      const d = new Date();
      d.setDate(d.getDate() + 1);
      d.setHours(0, 0, 0, 0);
      return d;
    },
  },
  {
    text: "上周此刻",
    onClick: () => {
      const d = new Date();
      d.setDate(d.getDate() - 7);
      d.setHours(0, 0, 0, 0);
      return d;
    },
  },
];

const removeMedium = (media: MomentMedia) => {
  let formMedium = formState.value.spec.content.medium;
  if (!formMedium) {
    return;
  }
  let index: number = formMedium.indexOf(media);
  if (index > -1) {
    formMedium.splice(index, 1);
  }
};

const handlerCancel = () => {
  emit("cancel");
};

const uploadMediumNum = 9;

const addMediumVerify = (media?: {
  url: string;
  cover?: string;
  displayName?: string;
  type?: string;
}) => {
  let formMedium = formState.value.spec.content.medium;
  if (!formMedium || formMedium.length == 0) {
    return true;
  }

  if (formMedium.length >= uploadMediumNum) {
    Toast.warning("最多允许添加 " + uploadMediumNum + " 个附件");
    return false;
  }

  if (media) {
    if (formState.value.spec.content.medium?.filter((item) => item.url == media.url).length != 0) {
      Toast.warning("已过滤重复添加的附件");
      return false;
    }
  }

  return true;
};

function handleToggleVisible() {
  // @unocss-skip-start
  const { visible: currentVisible } = formState.value.spec;
  // @unocss-skip-end
  formState.value.spec.visible = currentVisible === "PUBLIC" ? "PRIVATE" : "PUBLIC";
}
</script>

<template>
  <div class=":uno: card shrink overflow-hidden border rounded-md bg-white">
    <AttachmentSelectorModal
      v-model:visible="attachmentSelectorModal"
      v-permission="['system:attachments:view']"
      :min="1"
      :max="9"
      :accepts="accepts"
      @select="onAttachmentsSelect"
    />
    <TextEditor
      v-model:raw="formState.spec.content.raw"
      v-model:html="formState.spec.content.html"
      v-model:isEmpty="isEditorEmpty"
      :tag-query-fetch="useConsoleTagQueryFetch"
      class=":uno: min-h-[9rem]"
      tabindex="-1"
      @submit="handlerCreateOrUpdateMoment"
    />
    <div v-if="formState.spec.content.medium?.length" class=":uno: img-box flex px-3.5 py-2">
      <ul class=":uno: grid grid-cols-3 w-full gap-1.5 sm:w-1/2" role="list">
        <li
          v-for="(media, index) in formState.spec.content.medium"
          :key="index"
          class=":uno: inline-block overflow-hidden border rounded-md"
        >
          <MediaCard :media="media" @remove="removeMedium"></MediaCard>
        </li>
      </ul>
    </div>
    <div class=":uno: flex justify-between bg-white px-3.5 py-2">
      <div class=":uno: flex items-center space-x-2">
        <div
          class=":uno: group flex cursor-pointer items-center justify-center rounded-full p-2 hover:bg-sky-600/10"
        >
          <TablerPhoto
            class=":uno: size-full text-base text-gray-600 group-hover:text-sky-600"
            @click="addMediumVerify() && (attachmentSelectorModal = true)"
          />
        </div>
        <DatePicker
          v-model:value="releaseTimeDate"
          v-tooltip="{ content: '发布日期' }"
          type="date"
          format="YYYY-MM-DD"
          :editable="false"
          :clearable="false"
          :shortcuts="releaseTimeShortcuts"
          placeholder="发布日期"
          input-class=":uno: mx-input rounded moment-release-time-input"
          class=":uno: date-picker release-time-picker max-w-[10rem] cursor-pointer"
        />
        <div
          v-if="isUpdateMode"
          v-tooltip="{ content: '点赞数（可手动修改）' }"
          class=":uno: upvote-edit inline-flex h-7 items-center gap-1 rounded border bg-gray-50 px-2 text-xs text-gray-600 focus-within:border-sky-500 w-40"
          :class="upvoteChanged ? ':uno: border-sky-500 bg-sky-50' : ''"
        >
          <MingcuteHeartFill
            class=":uno: text-sm"
            :class="upvoteChanged ? ':uno: text-red-500' : ':uno: text-gray-400'"
          />
          <input
            v-model.number="upvoteEdit"
            type="number"
            min="0"
            step="1"
            class=":uno: w-12 border-0 bg-transparent text-xs outline-none"
            @keydown.stop
          />
        </div>
      </div>

      <div class=":uno: flex items-center space-x-2.5">
        <div
          v-tooltip="{
            content: formState.spec.visible === 'PRIVATE' ? `私有访问` : '公开访问',
          }"
          class=":uno: group flex cursor-pointer items-center justify-center rounded-full p-2"
          :class="
            formState.spec.visible === 'PRIVATE'
              ? ':uno: hover:bg-red-600/10'
              : ':uno: hover:bg-green-600/10'
          "
          @click="handleToggleVisible()"
        >
          <IconEyeOff
            v-if="formState.spec.visible === 'PRIVATE'"
            class=":uno: size-full text-base text-gray-600 group-hover:text-red-600"
          />
          <IconEye
            v-else
            class=":uno: size-full text-base text-gray-600 group-hover:text-green-600"
          />
        </div>

        <button
          v-if="isUpdateMode"
          class=":uno: h-7 inline-flex cursor-pointer items-center rounded px-3 text-gray-600 hover:bg-sky-600/10 hover:text-sky-600"
          @click="handlerCancel"
        >
          <span class=":uno: text-xs"> 取消 </span>
        </button>

        <div
          v-permission="['plugin:moments:manage', 'uc:plugin:moments:publish']"
          v-tooltip="saveDisabledReason ? { content: saveDisabledReason } : undefined"
          class=":uno: h-fit"
        >
          <VButton
            v-model:disabled="saveDisable"
            :loading="saving"
            size="sm"
            type="primary"
            @click="handlerCreateOrUpdateMoment"
          >
            <template #icon>
              <SendMoment class=":uno: size-full scale-[1.35]" />
            </template>
          </VButton>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss">
.release-time-picker {
  .mx-input {
    height: 1.75rem;
    padding: 0 1.75rem 0 0.5rem;
    font-size: 0.75rem;
    border-radius: 0.375rem;
    color: #4b5563;
    box-shadow: none;
  }

  .mx-input:hover {
    border-color: #0284c7;
  }

  .mx-icon-calendar,
  .mx-icon-clear {
    right: 0.375rem;
  }
}
</style>
