<script lang="ts" setup>
import type { ListedMoment } from "@/api/generated";
import ShikiDirective from "@/plugin-supports/shiki/directive";
import { IconArrowLeft, IconArrowRight, IconMessage } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { useQueryClient } from "@tanstack/vue-query";
import { computed, inject, ref } from "vue";
import LucideFileAudio from "~icons/lucide/file-audio";
import LucideFileVideo from "~icons/lucide/file-video";
import RiHeart3Line from "~icons/ri/heart-3-line";
import PreviewDetailModal from "./PreviewDetailModal.vue";

const props = defineProps<{
  moment: ListedMoment;
  uc: boolean;
}>();

const tagContext = inject<{ updateTagQuery: (tag: string) => void } | null>("tag", null);

const emit = defineEmits<{
  (event: "switchEditMode"): void;
}>();

const queryClient = useQueryClient();
const htmlParser = new DOMParser();

const vLazy = {
  mounted: (el: HTMLElement) => {
    const iframes = el.querySelectorAll<any>("iframe");
    iframes.forEach((iframe: any) => {
      iframe.loading = "lazy";
      iframe.importance = "low";
    });
    const imgs = el.querySelectorAll<HTMLImageElement>("img,image");
    imgs.forEach((img: HTMLImageElement) => {
      img.loading = "lazy";
    });
    const medium = el.querySelectorAll<HTMLMediaElement>("video,audio");
    medium.forEach((media: HTMLMediaElement) => {
      media.autoplay = false;
      media.preload = "metadata";
    });
  },
};

/** 后台展示用标签列表 */
const adminTags = computed(() => props.moment.moment.spec.tags || []);

/**
 * 渲染用 HTML：移除历史正文内嵌标签，避免前台样式泄露到后台预览。
 */
const previewHtml = computed(() => {
  const html = props.moment.moment.spec.content.html;
  if (!html) {
    return "";
  }
  const document = htmlParser.parseFromString(html, "text/html");
  document.querySelectorAll("a.tag").forEach((node) => node.remove());
  return document.body.innerHTML;
});

const mediums = ref(props.moment.moment.spec.content.medium || []);
const detailVisible = ref<boolean>(false);
const selectedIndex = ref<number>(0);
const selectedMedia = computed(() => {
  if (mediums.value.length == 0) {
    return undefined;
  }
  return mediums.value[selectedIndex.value];
});

const handleSwitchEdit = () => {
  emit("switchEditMode");
};

const handleClickMedium = (index: number) => {
  selectedIndex.value = index;
  detailVisible.value = true;
};

const getExtname = (type?: string) => {
  if (!type) {
    return "";
  }
  const ext = type.split("/");
  if (ext.length > 1) {
    if (ext) return ext[1].toLowerCase();
  }
  return "";
};

const upvoteCount = computed(() => props.moment.stats?.upvote ?? 0);

const commentText = computed(() => {
  const { totalComment, approvedComment } = props.moment.stats || {};

  let text = String(totalComment ?? 0);

  const pendingComments = (totalComment || 0) - (approvedComment || 0);

  if (pendingComments > 0) {
    text += `（${pendingComments} 条待审核）`;
  }
  return text;
});

const commentListVisible = ref(false);
const commentSubjectRefKey = `moment.halo.run/Moment/${props.moment.moment.metadata.name}`;

function onCommentListClose() {
  commentListVisible.value = false;

  queryClient.invalidateQueries({
    queryKey: ["plugin:moments:console:list"],
  });
}

function handleOpenCommentList() {
  if (props.uc) {
    window.open(`/moments/${props.moment.moment.metadata.name}`, "_blank");
    return;
  }
  commentListVisible.value = true;
}

/**
 * 点击后台标签徽章，触发列表筛选。
 * @param tagName 标签名称
 */
function handleTagFilter(tagName: string) {
  tagContext?.updateTagQuery(tagName);
}

defineOptions({
  directives: {
    shiki: ShikiDirective,
  },
});
</script>
<template>
  <PreviewDetailModal
    v-if="detailVisible && selectedMedia"
    :media="selectedMedia"
    @close="detailVisible = false"
  >
    <template #actions>
      <span @click="selectedIndex = (selectedIndex + mediums.length - 1) % mediums.length">
        <IconArrowLeft />
      </span>
      <span @click="selectedIndex = (selectedIndex + 1) % mediums.length">
        <IconArrowRight />
      </span>
    </template>
  </PreviewDetailModal>
  <div class=":uno: relative overflow-hidden" @dblclick="handleSwitchEdit">
    <div
      v-lazy
      v-shiki
      class=":uno: markdown-body moment-preview-html text-slate-800"
      v-html="previewHtml"
    ></div>

    <div
      v-if="!uc && adminTags.length"
      class=":uno: mt-3 flex flex-wrap gap-1.5"
      role="list"
      aria-label="瞬间标签"
    >
      <button
        v-for="tagName in adminTags"
        :key="tagName"
        type="button"
        class=":uno: moment-admin-tag inline-flex cursor-pointer items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-xs text-blue-700 transition-colors duration-200 hover:bg-blue-100"
        @click="handleTagFilter(tagName)"
      >
        #{{ tagName }}
      </button>
    </div>

    <div
      v-if="!!moment.moment.spec.content.medium && moment.moment.spec.content.medium.length > 0"
      class=":uno: img-box flex pt-2"
    >
      <ul class=":uno: grid grid-cols-3 w-full gap-1.5 sm:w-1/2 !pl-0" role="list">
        <li
          v-for="(media, index) in moment.moment.spec.content.medium"
          :key="index"
          class=":uno: inline-block cursor-pointer overflow-hidden rounded-lg border border-slate-200"
        >
          <div class=":uno: aspect-square" @click="handleClickMedium(index)">
            <template v-if="media.type == 'PHOTO'">
              <img
                :src="utils.attachment.getThumbnailUrl(media.url!, 'S')"
                class=":uno: size-full object-cover"
                loading="lazy"
              />
            </template>
            <template v-else-if="media.type == 'VIDEO'">
              <div
                class=":uno: size-full flex flex-col items-center justify-center bg-slate-100 space-y-1"
              >
                <LucideFileVideo />
                <span class=":uno: text-xs text-slate-500 font-sans">
                  {{ getExtname(media.originType) }}
                </span>
              </div>
            </template>
            <template v-else-if="media.type == 'AUDIO'">
              <div
                class=":uno: size-full flex flex-col items-center justify-center bg-slate-100 space-y-1"
              >
                <LucideFileAudio />
                <span class=":uno: text-xs text-slate-500 font-sans">
                  {{ getExtname(media.originType) }}
                </span>
              </div>
            </template>
          </div>
        </li>
      </ul>
    </div>

    <div class=":uno: mt-3 flex items-center gap-3 text-xs text-slate-500">
      <div class=":uno: inline-flex items-center gap-1">
        <RiHeart3Line class=":uno: size-3.5" />
        <span>{{ upvoteCount }}</span>
      </div>
      <div
        class=":uno: group inline-flex cursor-pointer items-center gap-1 hover:text-slate-700"
        @click="handleOpenCommentList"
      >
        <IconMessage class=":uno: size-3.5" />
        <span class=":uno: group-hover:underline">{{ commentText }}</span>
      </div>
    </div>

    <SubjectQueryCommentListModal
      v-if="commentListVisible && !uc"
      :subject-ref-key="commentSubjectRefKey"
      @close="onCommentListClose"
    />
  </div>
</template>
