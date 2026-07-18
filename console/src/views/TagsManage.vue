<script lang="ts" setup>
import { momentsConsoleApiClient } from "@/api";
import type { MomentTagVo } from "@/types";
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VModal,
  VPageHeader,
} from "@halo-dev/components";
import { useQuery, useQueryClient } from "@tanstack/vue-query";
import { refDebounced } from "@vueuse/core";
import { ref } from "vue";
import { useRouter } from "vue-router";
import MingcuteArrowLeftLine from "~icons/mingcute/arrow-left-line";
import MingcuteTag2Line from "~icons/mingcute/tag-2-line";

const router = useRouter();
const queryClient = useQueryClient();

const keyword = ref("");
const debouncedKeyword = refDebounced(keyword, 300);

const {
  data: tags,
  isLoading,
  refetch,
} = useQuery({
  queryKey: ["plugin:moments:tag-stats", debouncedKeyword],
  queryFn: async () => {
    const { data } = await momentsConsoleApiClient.listTagStats(
      debouncedKeyword.value || undefined
    );
    return data;
  },
  refetchOnWindowFocus: false,
});

const renameModalVisible = ref(false);
const renaming = ref(false);
const renameTarget = ref<MomentTagVo | null>(null);
const renameValue = ref("");

function openRenameModal(tag: MomentTagVo) {
  renameTarget.value = tag;
  renameValue.value = tag.name;
  renameModalVisible.value = true;
}

async function handleRename() {
  if (!renameTarget.value) {
    return;
  }
  const oldName = renameTarget.value.name;
  const newName = renameValue.value.trim();
  if (!newName) {
    Toast.warning("请输入新标签名称");
    return;
  }
  if (newName === oldName) {
    renameModalVisible.value = false;
    return;
  }
  renaming.value = true;
  try {
    const { data } = await momentsConsoleApiClient.renameTag(oldName, newName);
    if (data.failed > 0) {
      Toast.warning(`已更新 ${data.affected} 条，${data.failed} 条失败`);
    } else {
      Toast.success(`已重命名 ${data.affected} 条瞬间中的标签`);
    }
    renameModalVisible.value = false;
    await refetch();
    queryClient.invalidateQueries({ queryKey: ["plugin:moments:console:list"] });
    queryClient.invalidateQueries({ queryKey: ["moments-tags"] });
  } catch (error) {
    console.error(error);
  } finally {
    renaming.value = false;
  }
}

function handleDelete(tag: MomentTagVo) {
  Dialog.warning({
    title: `确定要删除标签「${tag.name}」吗？`,
    description: `将从 ${tag.momentCount} 条瞬间中移除该标签，此操作不可恢复。`,
    confirmType: "danger",
    onConfirm: async () => {
      try {
        const { data } = await momentsConsoleApiClient.deleteTag(tag.name);
        if (data.failed > 0) {
          Toast.warning(`已更新 ${data.affected} 条，${data.failed} 条失败`);
        } else {
          Toast.success(`已从 ${data.affected} 条瞬间中删除标签`);
        }
        await refetch();
        queryClient.invalidateQueries({ queryKey: ["plugin:moments:console:list"] });
        queryClient.invalidateQueries({ queryKey: ["moments-tags"] });
      } catch (error) {
        console.error(error);
      }
    },
  });
}

function handleView(tag: MomentTagVo) {
  router.push({ path: "/moments", query: { tag: tag.name } });
}

function handleBack() {
  router.push({ path: "/moments" });
}
</script>

<template>
  <VPageHeader title="标签管理">
    <template #icon>
      <MingcuteTag2Line />
    </template>
    <template #actions>
      <VButton @click="handleBack">
        <template #icon>
          <MingcuteArrowLeftLine class=":uno: size-full" />
        </template>
        返回瞬间列表
      </VButton>
    </template>
  </VPageHeader>

  <VCard class=":uno: m-0 flex-1 md:m-4">
    <div class=":uno: mx-auto max-w-4xl px-4 py-4 md:px-8">
      <div class=":uno: mb-4">
        <FormKit
          v-model="keyword"
          type="search"
          placeholder="搜索标签名称"
          outer-class=":uno: !mb-0"
          input-class=":uno: h-9 rounded-md border px-3 text-sm"
        />
      </div>

      <VLoading v-if="isLoading" />

      <VEmpty
        v-else-if="!tags || tags.length === 0"
        title="暂无标签"
        message="发布瞬间时选择标签后，会出现在这里。"
      />

      <div v-else class=":uno: overflow-hidden rounded-lg border">
        <table class=":uno: w-full text-left text-sm">
          <thead class=":uno: bg-gray-50 text-gray-600">
            <tr>
              <th class=":uno: px-4 py-3 font-medium">标签名称</th>
              <th class=":uno: px-4 py-3 font-medium">瞬间数量</th>
              <th class=":uno: px-4 py-3 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class=":uno: divide-y divide-gray-100">
            <tr v-for="tag in tags" :key="tag.name" class=":uno: bg-white hover:bg-gray-50">
              <td class=":uno: px-4 py-3 font-medium">{{ tag.name }}</td>
              <td class=":uno: px-4 py-3 text-gray-600">{{ tag.momentCount }}</td>
              <td class=":uno: px-4 py-3">
                <div class=":uno: flex items-center justify-end gap-2">
                  <VButton size="sm" @click="handleView(tag)">查看瞬间</VButton>
                  <VButton
                    v-permission="['plugin:moments:manage']"
                    size="sm"
                    @click="openRenameModal(tag)"
                  >
                    重命名
                  </VButton>
                  <VButton
                    v-permission="['plugin:moments:manage']"
                    size="sm"
                    type="danger"
                    @click="handleDelete(tag)"
                  >
                    删除
                  </VButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </VCard>

  <VModal
    v-model:visible="renameModalVisible"
    title="重命名标签"
    :width="420"
    @close="renameModalVisible = false"
  >
    <FormKit
      v-model="renameValue"
      type="text"
      label="新标签名称"
      validation="required"
      outer-class=":uno: !mb-0"
    />
    <template #footer>
      <VButton type="secondary" @click="renameModalVisible = false">取消</VButton>
      <VButton type="primary" :loading="renaming" @click="handleRename">保存</VButton>
    </template>
  </VModal>
</template>