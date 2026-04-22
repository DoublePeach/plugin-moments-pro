import { axiosInstance, coreApiClient, type Counter } from "@halo-dev/api-client";
import {
  ConsoleApiMomentHaloRunV1alpha1MomentApi,
  MomentV1alpha1Api,
  UcApiMomentHaloRunV1alpha1MomentApi,
} from "./generated";

const momentsCoreApiClient = {
  moment: new MomentV1alpha1Api(undefined, "", axiosInstance),
};

const CONSOLE_BASE = "/apis/console.api.moment.halo.run/v1alpha1/moments";

/** Returns the counter resource name for a moment by its metadata.name. */
export function momentCounterName(momentName: string) {
  return `moments.moment.halo.run/${momentName}`;
}

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
  /**
   * Fetch the counter (upvote / comment stats) of a single moment.
   *
   * We query via {@code listCounter} + fieldSelector instead of {@code getCounter}
   * for two reasons:
   * 1. Counter names contain '/', which requires URL-encoding. When unencoded
   *    (as Spring sometimes decodes path variables), Spring's NoResourceFound
   *    handler returns "No static resource ...", which the Halo console
   *    global error interceptor surfaces as an ugly toast.
   * 2. listCounter never 404s on a missing counter; it just returns an empty
   *    list. This is exactly the semantics we want: "null if not yet created".
   */
  async getCounter(momentName: string): Promise<Counter | null> {
    const name = momentCounterName(momentName);
    const { data } = await coreApiClient.metrics.counter.listCounter({
      page: 1,
      size: 1,
      fieldSelector: [`metadata.name=${name}`],
    });
    return data.items?.[0] ?? null;
  },
  /** Update the upvote count of a single moment. Preserves other fields. */
  async setUpvote(momentName: string, upvote: number): Promise<Counter> {
    const name = momentCounterName(momentName);
    const next = Math.max(0, Math.floor(upvote));
    const existing = await momentsConsoleApiClient.getCounter(momentName);
    if (existing) {
      const payload: Counter = {
        ...existing,
        upvote: next,
      };
      const { data } = await coreApiClient.metrics.counter.updateCounter({
        name,
        counter: payload,
      });
      return data;
    }
    const payload: Counter = {
      apiVersion: "metrics.halo.run/v1alpha1",
      kind: "Counter",
      metadata: { name },
      upvote: next,
      totalComment: 0,
      approvedComment: 0,
      visit: 0,
    };
    const { data } = await coreApiClient.metrics.counter.createCounter({
      counter: payload,
    });
    return data;
  },
};

const momentsUcApiClient = {
  moment: new UcApiMomentHaloRunV1alpha1MomentApi(undefined, "", axiosInstance),
};

export { momentsConsoleApiClient, momentsCoreApiClient, momentsUcApiClient };
