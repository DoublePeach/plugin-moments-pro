import type { Component } from "vue";

export interface MenuItem {
  type: "button" | "separator";
  icon?: Component;
  title?: string;
  action?: () => void;
  isActive?: () => boolean;
  children?: MenuItem[];
}

/** Tag statistics returned by the console tag-stats API. */
export interface MomentTagVo {
  name: string;
  permalink: string;
  momentCount: number;
}

/** Result of a tag rename or delete mutation. */
export interface TagMutationResult {
  affected: number;
  failed: number;
}