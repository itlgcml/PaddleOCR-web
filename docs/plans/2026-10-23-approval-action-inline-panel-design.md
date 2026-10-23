# 审批操作平铺面板设计（去弹窗）

日期：2026-10-23
状态：已获批（方案 A）

## 背景与目标

现状：`ApprovalActionBar.vue`（按钮栏）点击操作按钮后弹出 `ApprovalActionDialog.vue`（`el-dialog`，520px）填写表单。

目标：不合并组件，将弹窗交互改为平铺——进入页面默认展开第一个可用操作（通常为「通过」）的表单，点其他按钮切换表单，始终有一个在展示，不可收起。

## 决策（方案 A）

`ApprovalActionDialog.vue` 原地改造为平铺面板并重命名 `ApprovalActionPanel.vue`；`ApprovalActionBar.vue` 持有 `activeAction` 切换状态。否决：新建面板保留旧 Dialog（死代码、双份维护）；面板内联进 ActionBar（等于合并组件）。

## 交互规则

- 初始 `activeAction = actions[0]`（ACTION_ORDER 中 PASS 在首位，即默认展开「通过」表单）。
- 点其他按钮切换到对应表单；点当前已选中按钮保持展开不收起；当前选中按钮高亮。
- 切换操作时 `resetForm()`（沿用现状「每次打开重置」口径，避免残留）。
- `loading`（提交中）禁用全部按钮与表单控件。
- 减签（REDUCE_SIGN）提交后面板保持展开、提示列表随刷新更新（现状口径不变）。
- 其他操作提交成功后 `reset()`：清空表单并回到默认操作；提交失败保持当前表单可重试。

## 布局

- 面板位于按钮栏正下方，宽度撑满容器（不再固定 520px）；白底 + 1px 边框 + 8px 圆角，与详情页卡片风格一致。
- 面板标题 = 当前操作名；底部仅保留提交按钮（去掉「取消」）。
- 表单 `label-position` 由 `top` 改 `left`（label-width 约 90px）；人工路由、加签减签提示、子流程审批人等动态区块渲染逻辑不动。

## 改动文件

| 文件 | 改动 |
|---|---|
| `frontend/src/components/workflow/ApprovalActionDialog.vue` | 重命名为 `ApprovalActionPanel.vue`，`el-dialog` → 平铺卡片，去「取消」按钮，`label-position="left"` |
| `frontend/src/components/workflow/ApprovalActionBar.vue` | 默认展开 + 按钮切换/高亮逻辑；引用改名；expose `close()` → `reset()` |
| `frontend/src/views/approval/components/ApprovalStatusBar.vue` | expose `close` → `reset`（透传） |
| `frontend/src/views/approval/InstanceDetailView.vue` | `barRef.value?.close()` → `barRef.value?.reset()`（1 行），注释同步 |

`approval-actions.ts` 与 props/emits/payload 类型均不变。

## 错误处理

无新增错误路径；提交失败提示仍由 Axios 拦截器统一处理，面板保持当前状态。

## 测试要点

默认展开 PASS；切换操作表单重置；必填校验（拒绝/退回/转办等）；loading 锁定；减签后面板保持展开且列表刷新；提交成功后重置回默认操作；PASS 带人工路由/子流程审批人场景正常。
