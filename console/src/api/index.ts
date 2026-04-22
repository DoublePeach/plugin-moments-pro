import { axiosInstance } from "@halo-dev/api-client";
import {
  ConsoleApiMomentHaloRunV1alpha1MomentApi,
  MomentV1alpha1Api,
  UcApiMomentHaloRunV1alpha1MomentApi,
} from "./generated";

const momentsCoreApiClient = {
  moment: new MomentV1alpha1Api(undefined, "", axiosInstance),
};

const CONSOLE_BASE = "/apis/console.api.moment.halo.run/v1alpha1/moments";

/** Counter resource for a moment. Matches Halo core {@code Counter} extension. */
export interface MomentCounter {
  apiVersion?: string;
  kind?: string;
  metadata: {
    name: string;
    version?: number;
  };
  upvote?: number;
  totalComment?: number;
  approvedComment?: number;
  visit?: number;
}

/** Returns the counter resource name for a moment by its metadata.name. */
export function momentCounterName(momentName: string) {
  return `moments.moment.halo.run/${momentName}`;
}

const COUNTER_BASE = "/apis/metrics.halo.run/v1alpha1/counters";

const momentsConsoleApiClient = {
  moment: new ConsoleApiMomentHaloRunV1alpha1MomentApi(undefined, "", axiosInstance),
  /** Pin a moment to the top. */
  pin(name: string) {
    return axiosInstance.put(`${CONSOLE_BASE}/${name}/pin`);
  },
  /** Remove pin from a moment. */
  unpin(name: string) {
    return axiosInstance.put(`${CONSOLE_BASE}/${name}/unpin`);
  },
  /** Reorder pinned moments. First name becomes the top one. */
  reorderPinned(names: string[]) {
    return axiosInstance.put(`${CONSOLE_BASE}/-/pin-order`, { names });
  },
  /** Delete moments in batch. */
  deleteBatch(names: string[]) {
    return axiosInstance.post<{ deleted: number }>(
      `${CONSOLE_BASE}/-/delete-batch`,
      { names }
    );
  },
  /** Approve moments in batch. */
  approveBatch(names: string[]) {
    return axiosInstance.post<{ updated: number }>(
      `${CONSOLE_BASE}/-/approve-batch`,
      { names }
    );
  },
  /** Update visibility of moments in batch. */
  setVisibleBatch(names: string[], visible: "PUBLIC" | "PRIVATE") {
    return axiosInstance.post<{ updated: number }>(
      `${CONSOLE_BASE}/-/visible-batch`,
      { names, visible }
    );
  },
  /** Fetch the counter (upvote / comment stats) of a single moment. */
  async getCounter(momentName: string) {
    const name = momentCounterName(momentName);
    try {
      const { data } = await axiosInstance.get<MomentCounter>(`${COUNTER_BASE}/${name}`);
      return data;
    } catch (e) {
      if ((e as { response?: { status?: number } })?.response?.status === 404) {
        return null;
      }
      throw e;
    }
  },
  /** Update the upvote count of a single moment. Preserves other fields. */
  async setUpvote(momentName: string, upvote: number) {
    const name = momentCounterName(momentName);
    const existing = await momentsConsoleApiClient.getCounter(momentName);
    if (existing) {
      const payload: MomentCounter = {
        ...existing,
        upvote: Math.max(0, Math.floor(upvote)),
      };
      const { data } = await axiosInstance.put<MomentCounter>(
        `${COUNTER_BASE}/${name}`,
        payload
      );
      return data;
    }
    const payload: MomentCounter = {
      apiVersion: "metrics.halo.run/v1alpha1",
      kind: "Counter",
      metadata: { name },
      upvote: Math.max(0, Math.floor(upvote)),
      totalComment: 0,
      approvedComment: 0,
      visit: 0,
    };
    const { data } = await axiosInstance.post<MomentCounter>(COUNTER_BASE, payload);
    return data;
  },
};

const momentsUcApiClient = {
  moment: new UcApiMomentHaloRunV1alpha1MomentApi(undefined, "", axiosInstance),
};

export { momentsConsoleApiClient, momentsCoreApiClient, momentsUcApiClient };
