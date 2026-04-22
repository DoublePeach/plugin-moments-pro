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
};

const momentsUcApiClient = {
  moment: new UcApiMomentHaloRunV1alpha1MomentApi(undefined, "", axiosInstance),
};

export { momentsConsoleApiClient, momentsCoreApiClient, momentsUcApiClient };
