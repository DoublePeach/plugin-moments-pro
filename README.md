# plugin-moments

> **Halo 瞬间插件 v3.0** — 轻量图文动态发布与管理
>
> 作者：[Tovin](https://blog.tovin.xyz)

基于 [halo-sigs/plugin-moments](https://github.com/halo-sigs/plugin-moments) 二次增强，面向独立博客与个人站点，提供更顺手的后台管理体验与更纯粹的前台展示。

---

## ✨ v3.0 核心特性

| 能力 | 说明 |
| --- | --- |
| 标签下拉选择 | 发布/编辑时通过下拉框选择标签（支持模糊搜索、多选、新建），不再使用正文 `#话题` 输入 |
| 标签仅后台可见 | 标签用于后台展示与筛选，**前台不展示标签**（正文与 API 均已剥离） |
| 无审核流程 | 发布即公开，移除待审核/批量审核相关能力 |
| 置顶管理 | 列表项菜单支持置顶/取消置顶；置顶后**仍保留在原列表位置**，仅显示置顶标记 |
| 点赞数初始化 | 发布与编辑时均可手动设置点赞数（支持三位数） |
| 紧凑后台 UI | 标准管理后台布局：单行筛选、小字号、信息密度更高 |
| 发布日期 | 新建/编辑可自定义发布日期，内置快捷项 |
| 批量操作 | 批量删除、批量改可见性 |
| 导出本页 | 一键导出当前列表 JSON |

---

## 🎯 适用场景

- 独立博客碎碎念、读书随笔、探店打卡
- 团队内部动态、发版记录
- 需要补录历史内容并自定义发布时间的数字花园

插件能力一览：

1. 富文本编辑器，支持图文、视频、音频、代码高亮（配合 Shiki 插件）
2. 公开 / 私有可见性控制
3. 前台 `/moments` 路由 + 详情页 + RSS（`/moments/rss.xml`）
4. 接入 Halo 全站搜索（`moment.moment.halo.run`）
5. 用户中心（UC）支持用户自行发布与管理瞬间
6. Finder API，便于主题二次集成

---

## 🚀 安装与使用

### 环境要求

- Halo **>= 2.22.0**
- Java 17

### 安装

1. 下载 `plugin-moments-3.0.0.jar`（Release 或自行构建）
2. Halo 后台 → **插件** → 上传安装 → 启用
3. 左侧菜单出现 **瞬间**，即可管理
4. 前台访问 `/moments`（需主题提供 `moments.html` 模板）

### 后台操作速览

- **发布**：填写正文 → 选择日期、标签、点赞数 → 发送
- **筛选**：标签 / 可见性 / 排序 / 作者 / 日期范围（单行紧凑布局）
- **置顶**：列表项 `⋯` 菜单 → 置顶 / 取消置顶
- **标签管理**：顶部「标签管理」入口，支持重命名与删除

---

## 🛠 开发

```bash
git clone <your-repo>
cd plugin-moments

# Windows
./gradlew.bat pnpmInstall
./gradlew.bat haloServer

# macOS / Linux
./gradlew pnpmInstall
./gradlew haloServer
```

构建插件 JAR：

```bash
./gradlew build
# 产物：build/libs/plugin-moments-3.0.0.jar
```

开发文档：<https://docs.halo.run/developer-guide/plugin/introduction>

---

## 📡 API 说明

### 公开 API

- `GET /apis/api.moment.halo.run/v1alpha1/moments` — 列表
- `GET /apis/api.moment.halo.run/v1alpha1/moments/{name}` — 详情

> v3.0 起，公开 API 返回的内容**不包含标签**（`spec.tags` 为空，正文内 `a.tag` 已剥离）。

### 后台 Console API（节选）

| Method | Path | 描述 |
| --- | --- | --- |
| `PUT` | `/moments/{name}/pin` | 置顶 |
| `PUT` | `/moments/{name}/unpin` | 取消置顶 |
| `PUT` | `/moments/-/pin-order` | 调整置顶顺序 |
| `POST` | `/moments/-/delete-batch` | 批量删除 |
| `POST` | `/moments/-/visible-batch` | 批量改可见性 |
| `GET` | `/tag-stats` | 标签统计 |
| `POST` | `/tags/-/rename` | 重命名标签 |
| `POST` | `/tags/-/delete` | 删除标签 |

后台列表支持 `pinSort=false` 查询参数：置顶项不自动排到列表顶部，便于管理。

点赞数通过 Halo Counter API 维护：

- `GET/PUT /apis/metrics.halo.run/v1alpha1/counters/moments.moment.halo.run/{momentName}`

---

## 🎨 主题适配

插件提供 `/moments` 路由，模板为 `moments.html`。

### 列表页变量

- `moments`：`UrlContextListResult<MomentVo>`
- ~~`tags`~~：v3.0 起不再向模板传递标签云变量

### Finder API

```html
<th:block th:with="moments = ${momentFinder.list({ page: 1, size: 10, sort: {'spec.releaseTime,desc'} })}">
  <ul>
    <li th:each="moment : ${moments.items}" th:utext="${moment.spec.content.html}"></li>
  </ul>
</th:block>
```

---

## 📝 更新日志

### v3.0.0

- 标签改为下拉选择，移除正文 `#` 输入
- 前台隐藏标签，仅后台展示与筛选
- 移除瞬间审核流程
- 后台 UI 重构：紧凑布局、小字号、单行筛选
- 置顶后保留在列表原位置（`pinSort=false`）
- 发布/编辑均可设置点赞数
- 列表项菜单增加置顶/取消置顶

### 历史增强（基于官方版）

- 自定义发布日期与快捷选择
- 置顶管理弹窗与顺序调整
- 批量操作与导出本页
- UC 可见性/排序筛选
- `Ctrl+Enter` 快捷发布

---

## 👤 作者

**[Tovin](https://blog.tovin.xyz)**

## 📄 License

[GPL-3.0](./LICENSE)