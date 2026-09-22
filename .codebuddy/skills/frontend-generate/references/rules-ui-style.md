# 分域规则：样式 / 布局 / 视觉 / 可访问性

> 覆盖上游：`unocss`（条件）、`antfu-design`（条件）、`web-design-guidelines`

## 1. 本项目当前样式方案（优先遵守）

| 项 | 约定 |
|---|---|
| 组件样式 | `<style scoped>`，禁全局污染 |
| 全局样式 | 仅 `src/styles/` 下，`main.ts` 引入 |
| UI 库 | Element Plus 2.14.6 按需引入，优先用其组件/布局（`el-row/el-col`、`el-container`、`el-space`） |
| 图标 | `@element-plus/icons-vue` 组件式：`<el-icon><Upload /></el-icon>` |
| 主题 | 用 Element Plus CSS 变量覆盖，禁直接改 EP 源码样式 |
| 间距/圆角/阴影 | 统一在 `src/styles/` 定义 CSS 变量，组件内引用 |

> ⚠️ 本项目**未引入 UnoCSS**。上游 `unocss` / `antfu-design` 的原子类写法（如 `flex items-center gap-4`）**不适用**，
> 生成时一律写成 scoped CSS + EP 布局组件。若项目后续引入 UnoCSS，再启用这两个技能。

## 2. 布局与视觉（来自 `web-design-guidelines` / `antfu-design`）

- 移动端优先 + 渐进增强；断点与 EP 的响应式保持一致
- 内容区最大宽度收敛，长文本限制行长（约 60–80 字符）
- 层级：标题/正文/辅助文字三级字号对比明显；主色仅用于主操作
- 交互反馈三态齐全：**加载 / 空 / 错误**；删除等破坏性操作必须二次确认
- 焦点态可见（`:focus-visible`），禁 `outline: none` 而无替代样式
- 暗色模式若启用，用语义化 token（`--bg` / `--text` / `--border`），禁散落硬编码色值

## 3. 可访问性（a11y，强制项）

- 图标按钮必须有 `aria-label` 或 `title`
- 表单控件关联 `<label>` 或 `aria-label`；错误提示用 `aria-describedby`
- 颜色不单独承载语义（错误态同时配图标/文案）
- 对比度达 WCAG AA（正文 ≥ 4.5:1）
- 弹窗/抽屉打开时焦点移入、关闭时归还；列表更新用 `aria-live="polite"`

## 4. 样式红线

- 禁 `!important`（EP 覆盖优先用 CSS 变量或 `:deep()`）
- 覆盖 EP 内部样式需 `<style scoped>` + `:deep(.el-xxx)`
- 禁写死像素魔法值堆砌；重复出现的尺寸抽 CSS 变量
- 禁在 `style` 里引入远程字体/资源（离线与合规风险）

## 5. OCR 场景专项

- 结果列表：长文本 `overflow-wrap: anywhere`，禁溢出容器
- 图片预览区固定宽高比 + `object-fit: contain`，避免布局抖动
- 上传区明确可接受类型与大小限制，并在前端预校验后给出文案提示
- 大文本结果（OCR 全文）**禁**整段塞进 `title` 属性或 `aria-label`
