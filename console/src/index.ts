import type { Extension } from "@halo-dev/api-client";
import { type CommentSubjectRefResult, definePlugin, utils } from "@halo-dev/ui-shared";
import { VLoading } from "@halo-dev/components";
import "uno.css";
import { defineAsyncComponent, markRaw } from "vue";
import MingcuteMomentsLine from "~icons/mingcute/moment-line";
import type { Moment } from "./api/generated";
import "./styles/index.scss";

/** 标签管理页异步组件，避免在插件入口同步加载导致控制台 bundle 初始化失败 */
const TagsManagePage = defineAsyncComponent({
  loader: () => import("@/views/TagsManage.vue"),
  loadingComponent: VLoading,
});

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/moments",
        name: "Moments",
        component: () => import("@/views/MomentsList.vue"),
        meta: {
          permissions: ["plugin:moments:view"],
          menu: {
            name: "瞬间",
            group: "content",
            icon: markRaw(MingcuteMomentsLine),
          },
        },
      },
    },
    {
      parentName: "Root",
      route: {
        path: "/moments/tags",
        name: "MomentTags",
        component: TagsManagePage,
        meta: {
          permissions: ["plugin:moments:view"],
        },
      },
    },
  ],
  ucRoutes: [
    {
      parentName: "Root",
      route: {
        path: "/moments",
        name: "Moments",
        component: () => import("@/uc/MomentsList.vue"),
        meta: {
          permissions: ["uc:plugin:moments:publish"],
          menu: {
            name: "瞬间",
            group: "content",
            icon: markRaw(MingcuteMomentsLine),
          },
        },
      },
    },
  ],
  extensionPoints: {
    "comment:subject-ref:create": () => {
      return [
        {
          kind: "Moment",
          group: "moment.halo.run",
          resolve: (subject: Extension): CommentSubjectRefResult => {
            const moment = subject as Moment;
            return {
              label: "瞬间",
              title: determineMomentTitle(moment),
              externalUrl: `/moments/${moment.metadata.name}`,
              route: {
                name: "Moments",
              },
            };
          },
        },
      ];
    },
  },
});

const determineMomentTitle = (moment: Moment) => {
  const pureContent = stripHtmlTags(moment.spec.content.raw || "");
  const title = !pureContent?.trim()
    ? utils.date.format(new Date(moment.spec.releaseTime || ""))
    : pureContent;
  return title?.substring(0, 100);
};

const stripHtmlTags = (str: string) => {
  const stripped = str?.replace(/<\/?[^>]+(>|$)/gi, "") || "";
  return stripped.replace(/\n/g, " ").replace(/\s+/g, " ");
};