<script lang="ts" setup>
import { momentsConsoleApiClient, momentsCoreApiClient } from "@/api";
import type { Moment, MomentMedia, MomentMediaTypeEnum } from "@/api/generated";
import MediaCard from "@/components/MediaCard.vue";
import TagSelectDropdown from "@/components/TagSelectDropdown.vue";
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
const htmlParser = new DOMParser();

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

/** 已选标签，双向绑定到 spec.tags */
const selectedTags = computed({
  get: () => formState.value.spec.tags || [],
  set: (tags: string[]) => {
    formState.value.spec.tags = tags;
  },
});

/**
 * 保存前清理正文中的历史 # 标签标记，标签仅通过下拉框维护。
 */
function stripLegacyTagsFromContent() {
  const content = formState.value.spec.content;
  (["raw", "html"] as const).forEach((field) => {
    const html = content[field];
    if (!html) {
      return;
    }
    const document = htmlParser.parseFromString(html, "text/html");
    document.querySelectorAll("a.tag").forEach((node) => node.remove());
    content[field] = document.body.innerHTML;
  });
}

const editorRaw = computed({
  get: () => formState.value.spec.content.raw ?? "",
  set: (value: string) => {
    formState.value.spec.content.raw = value;
  },
});

const editorHtml = computed({
  get: () => formState.value.spec.content.html ?? "",
  set: (value: string) => {
    formState.value.spec.content.html = value;
  },
});

const handlerCreateOrUpdateMoment = async () => {
  if (saveDisable.value) {
    return;
  }
  try {
    saving.value = true;
    stripLegacyTagsFromContent();
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

  queryClient.invalidateQueries({ queryKey: ["plugin:moments:console:list"] });

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

  queryClient.invalidateQueries({ queryKey: ["plugin:moments:console:list"] });

  Toast.success("发布成功");
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
    const fileType = media.type.split("/")[0];
    formState.value.spec.content.medium?.push({
      type: mediumWhitelist.get(fileType),
      url: media.url,
      originType: media.type,
    } as MomentMedia);
  });
};

const tagsChanged = computed(() => {
  const oldTags = [...(props.moment?.spec.tags || [])].sort().join(",");
  const newTags = [...(formState.value.spec.tags || [])].sort().join(",");
  return oldTags !== newTags;
});

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
      && !tagsChanged.value
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
  const formMedium = formState.value.spec.content.medium;
  if (!formMedium) {
    return;
  }
  const index: number = formMedium.indexOf(media);
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
  const formMedium = formState.value.spec.content.medium;
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
  <div class=":uno: moment-compose-card shrink overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
    <AttachmentSelectorModal
      v-model:visible="attachmentSelectorModal"
      v-permission="['system:attachments:view']"
      :min="1"
      :max="9"
      :accepts="accepts"
      @select="onAttachmentsSelect"
    />
    <TextEditor
      v-model:raw="editorRaw"
      v-model:html="editorHtml"
      v-model:isEmpty="isEditorEmpty"
      class=":uno: min-h-[9rem]"
      tabindex="-1"
      @submit="handlerCreateOrUpdateMoment"
    />
    <div v-if="formState.spec.content.medium?.length" class=":uno: img-box flex px-4 py-2">
      <ul class=":uno: grid grid-cols-3 w-full gap-2 sm:w-1/2" role="list">
        <li
          v-for="(media, index) in formState.spec.content.medium"
          :key="index"
          class=":uno: inline-block overflow-hidden rounded-lg border border-slate-200"
        >
          <MediaCard :media="media" @remove="removeMedium"></MediaCard>
        </li>
      </ul>
    </div>
    <div class=":uno: moment-compose-toolbar flex justify-between border-t border-slate-100 bg-slate-50/80 px-4 py-2.5">
      <div class=":uno: flex flex-wrap items-center gap-2">
        <button
          type="button"
          aria-label="添加附件"
          class=":uno: group flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg border border-transparent transition-colors duration-200 hover:border-blue-200 hover:bg-blue-50"
          @click="addMediumVerify() && (attachmentSelectorModal = true)"
        >
          <TablerPhoto
            class=":uno: size-4 text-slate-500 transition-colors group-hover:text-blue-600"
          />
        </button>
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
        <TagSelectDropdown v-model="selectedTags" />
        <div
          v-if="isUpdateMode"
          v-tooltip="{ content: '点赞数（可手动修改）' }"
          class=":uno: upvote-edit inline-flex h-7 items-center gap-1 rounded-md border border-slate-200 bg-white px-2 text-xs text-slate-600 transition-colors focus-within:border-blue-500 w-40"
          :class="upvoteChanged ? ':uno: border-blue-500 bg-blue-50' : ''"
        >
          <MingcuteHeartFill
            class=":uno: text-sm"
            :class="upvoteChanged ? ':uno: text-red-500' : ':uno: text-slate-400'"
          />
          <input
            v-model.number="upvoteEdit"
            type="number"
            min="0"
            step="1"
            aria-label="点赞数"
            class=":uno: w-12 border-0 bg-transparent text-xs outline-none"
            @keydown.stop
          />
        </div>
      </div>

      <div class=":uno: flex items-center gap-2">
        <button
          type="button"
          v-tooltip="{
            content: formState.spec.visible === 'PRIVATE' ? `私有访问` : '公开访问',
          }"
          class=":uno: group flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg border border-transparent transition-colors duration-200"
          :class="
            formState.spec.visible === 'PRIVATE'
              ? ':uno: hover:border-red-200 hover:bg-red-50'
              : ':uno: hover:border-emerald-200 hover:bg-emerald-50'
          "
          @click="handleToggleVisible()"
        >
          <IconEyeOff
            v-if="formState.spec.visible === 'PRIVATE'"
            class=":uno: size-4 text-slate-500 transition-colors group-hover:text-red-600"
          />
          <IconEye
            v-else
            class=":uno: size-4 text-slate-500 transition-colors group-hover:text-emerald-600"
          />
        </button>

        <button
          v-if="isUpdateMode"
          type="button"
          class=":uno: h-8 inline-flex cursor-pointer items-center rounded-lg px-3 text-sm text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
          @click="handlerCancel"
        >
          取消
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
    color: #475569;
    border-color: #e2e8f0;
    box-shadow: none;
    transition: border-color 0.2s ease;
  }

  .mx-input:hover,
  .mx-input:focus {
    border-color: #2563eb;
  }

  .mx-icon-calendar,
  .mx-icon-clear {
    right: 0.375rem;
  }
}
</style>