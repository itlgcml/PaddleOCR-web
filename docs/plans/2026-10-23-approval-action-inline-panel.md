# 审批操作平铺面板实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 审批操作从「点按钮弹窗」改为「平铺面板」：默认展开第一个可用操作表单，点其他按钮切换，始终有一个在展示，不可收起。

**Architecture:** `ApprovalActionDialog.vue` 原地改造并重命名 `ApprovalActionPanel.vue`（el-dialog → 平铺卡片）；`ApprovalActionBar.vue` 持有 `activeAction` 切换状态、平铺内嵌面板；`ApprovalStatusBar.vue` 与 `InstanceDetailView.vue` 的 `close()` 改 `reset()`。props/emits/payload 类型不变。

**Tech Stack:** Vue 3.5 `<script setup>` + TS + Element Plus。

**设计文档:** `docs/plans/2026-10-23-approval-action-inline-panel-design.md`

**约束:** 前端无测试框架，验证以 `npx vue-tsc --noEmit` + 人工验收替代；构建/检查命令执行前需用户确认（项目约定默认不主动执行构建命令）。

---

### Task 1: 新建 ApprovalActionPanel.vue（改造自 ApprovalActionDialog.vue），删除旧文件

**Files:**
- Create: `frontend/src/components/workflow/ApprovalActionPanel.vue`
- Delete: `frontend/src/components/workflow/ApprovalActionDialog.vue`

**改造要点（script，其余逻辑逐段原样保留）：**
1. props：去掉 `visible` 相关；`action: ApprovalActionKey` 必填非空（面板始终渲染）。
2. `watch([visible, props.action])` → `watch(() => props.action, () => resetForm(), { immediate: true })`。
3. 删除 `beforeClose`（无弹窗关闭语义）。
4. `meta` computed 去掉 null 分支（action 非空），template 中 `meta?.` 相应简化为 `meta.`。
5. expose 改为 `{ resetForm }`。
6. 注释中「弹窗」字样改「面板」。

**改造要点（template）：**
1. `el-dialog` 整体替换为 `div.approval-panel` 卡片：新增头部（标题 = `meta.label`），原 el-form 主体逐段保留，footer `div.approval-panel-footer` 只保留提交按钮循环（删除「取消」按钮）。
2. el-form 加 `label-position="left"` `label-width="96px"`（原 top）。
3. 减签/人工路由/分支审批人/子流程审批人/字段循环五段渲染逻辑不变。

**新增样式：** `.approval-panel`（浅灰底 `#fafbfc`、1px `#e4e7ed` 边框、8px 圆角、padding）、`.approval-panel-header`（底边框分隔）、`.approval-panel-title`、`.approval-panel-footer`（flex 右对齐）；原表单/提示样式保留，间距 20px 收紧为 16px，`.sub-process-assignees` 底色改白色。

**Step:** 建新文件 → 删旧文件 → 全局搜 `ApprovalActionDialog` 确认仅剩 Task 2 待改的引用。

---

### Task 2: 改造 ApprovalActionBar.vue（默认展开 + 切换高亮 + 平铺面板）

**Files:**
- Modify: `frontend/src/components/workflow/ApprovalActionBar.vue`

**改造要点：**
1. import 改 `ApprovalActionPanel.vue`。
2. `visible` defineModel 删除；`activeAction` 初始为 null，watch `props.actions`（immediate）：`activeAction` 为空或不在新列表 → 置为 `(props.actions ?? ACTION_ORDER)[0]`；仍在列表则保持。
3. `openDialog` → `selectAction(key)`：`loading` 或 `key === activeAction` 时 return，否则切换。
4. expose `close()` → `reset()`：`activeAction = (props.actions ?? ACTION_ORDER)[0]`，再调 `panelRef.value?.resetForm()`（Panel 的 action watch 与显式 resetForm 幂等，双保险）。
5. template：外层包 `div.approval-actions`；`div.approval-bar` 内按钮选中态——`:plain="item.skin === 'plain' && item.key !== activeAction"`（通过/拒绝/评论选中变实色）+ `:class="{ 'is-active': item.skin === 'text' && item.key === activeAction }"`（文字按钮选中主色）；面板平铺在按钮栏后，`v-if="activeAction"`，`:action="activeAction!"`，其余 props 原样转发，`@submit` 透传。
6. 新增样式 `.approval-bar .is-active`（主色文字 + 字重 600 + hover 保持主色）。

---

### Task 3: ApprovalStatusBar.vue 透传改名

**Files:**
- Modify: `frontend/src/views/approval/components/ApprovalStatusBar.vue:100-103`

`defineExpose({ close: () => barRef.value?.close() })` → `defineExpose({ reset: () => barRef.value?.reset() })`，注释改「提交成功后重置操作面板（回默认操作并清空表单）」。

---

### Task 4: InstanceDetailView.vue 调用改名 + 注释口径更新

**Files:**
- Modify: `frontend/src/views/approval/InstanceDetailView.vue:103-114`

1. `barRef.value?.close()` → `barRef.value?.reset()`。
2. 注释：「保持弹窗打开」→「保持面板展开」；「弹窗保持打开允许重试」→「面板保持展开允许重试」。

---

### Task 5: 全局校验 + 文档同步

**Step 1:** 全局搜 `ApprovalActionDialog|barRef.value?.close` 确认零残留；读 lint 诊断确认无错误。
**Step 2（需用户确认后执行）:** `cd frontend && npx vue-tsc --noEmit`，预期无类型错误。
**Step 3:** `backend-docs/工作流提交与审批操作接口设计.md` 在审批操作前端交互相关小节补记：操作栏平铺面板交互约定（默认展开首个、点击切换不收起、切换重置、减签保持展开、提交成功 reset 回默认）。

## 人工验收要点

进入待办详情默认展开「通过」表单；点拒绝/退回等切换且表单重置；必填校验生效；提交中按钮与控件锁定；减签后面板保持展开且列表刷新；提交成功重置回默认「通过」；PASS 带人工路由/子流程审批人场景正常。
