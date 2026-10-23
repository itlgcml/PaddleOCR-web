---
name: 子流程节点（SUB_PROCESS）前后端实现
overview: 按已定稿的《工作流子流程节点设计.md》（13 章设计文档）落地 SUB_PROCESS 子流程节点：后端含 DDL 脚本 06、FlowInstanceDO 增 6 字段、错误码 40065~40068、发布校验递归化、引擎树级流转（含 advanceMode WAIT/IMMEDIATE 双模式）、Service/VO 适配；前端含类型扩展、设计器视图栈钻入编辑、递归校验、运行态徽标与子实例卡片。按里程碑 M1~M5 顺序实施。
todos:
  - id: m1-schema-contract
    content: 用 [skill:java-p3c-generate] 生成 06 脚本、FlowInstanceDO 增字段、ResultCode 40065~40068、常量与 max-depth 配置
    status: completed
  - id: m2-validator
    content: 用 [skill:java-p3c-generate] 抽出 ProcessDesignValidator 递归校验 subDoc（深度/摆放/advanceMode）并接入发布链路
    status: completed
    dependencies:
      - m1-schema-contract
  - id: m3-engine
    content: 用 [skill:java-p3c-generate] 实现引擎：advance 分支、buildSubInstance/completeSubInstance/cascadeTreeTerminal、树级锁、loadDesign、预览穿透
    status: completed
    dependencies:
      - m1-schema-contract
  - id: m4-service-vo
    content: 用 [skill:java-p3c-generate] 适配 Service/VO：发起撤销列表根口径、detail subInstances、可见性、40068
    status: completed
    dependencies:
      - m3-engine
  - id: m2b-fe-designer
    content: 用 [skill:frontend-generate] 实现设计器：类型扩展、视图栈钻入、卡片徽标、推进方式单选、递归校验、深度限制
    status: completed
    dependencies:
      - m1-schema-contract
  - id: m5-fe-runtime
    content: 用 [skill:frontend-generate] 实现运行态展示：待办徽标、详情子实例卡片、返回父流程，并全链路回归旧流程
    status: completed
    dependencies:
      - m4-service-vo
      - m2b-fe-designer
---

## 用户需求
依据已定稿的《backend-docs/工作流子流程节点设计.md》（2026-10-22，13 章设计文档），生成子流程节点（SUB_PROCESS）的前后端完整实施计划。

## 产品概述
在既有审批流体系新增子流程节点：设计器点击节点钻入子流程视图继续设计（与主设计页面完全同构）；运行态父流程推进到该节点自动创建子流程实例独立流转，按节点配置的推进模式（WAIT 等待/IMMEDIATE 启动即继续）决定父流程行为。

## 核心功能
- 设计态：SUB_PROCESS 节点内嵌 subDoc（随父 design_json 保存/发布/快照）；视图栈钻入编辑+面包屑；推进方式单选；发布校验递归（深度≤3、分支列禁入、advanceMode 合法值）
- 运行态：子实例同表（flow_instance 增 6 列）；WAIT 完成回推父流程/拒绝父树终止，IMMEDIATE 旁路不回推不传播；树级推进锁与整树级联取消；树级锁+根实例口径（发起/我发起的/单据状态）
- 展示态：待办/已办子流程任务徽标；详情 subInstances 卡片逐级进入+返回父流程；错误码 40065~40068

## Tech Stack（复用既有栈，设计文档已定稿）
- 后端：JDK 8 + Spring Boot 2.7.18 + MyBatis-Plus，Maven 多模块（ocr-common/ocr-dal/ocr-service/ocr-web）
- 前端：Vue 3.5 + TypeScript + Pinia + Element Plus，Vite
- 数据：MySQL 脚本 `backend/sql/06-sub-process-schema.sql`（flow_instance 增 6 列 + 2 索引 + 存量回填 root_instance_id=id）

## Implementation Approach
严格按设计文档 §4/§5/§12 实施，零设计决策残留：
- **内嵌式**：`props.subDoc` 随父 design_json 走既有保存/发布/快照链路，配置域无新表新接口
- **引擎扩展**（FlowEngineImpl）：advance 增 SUB_PROCESS 分支 → buildSubInstance（身份继承父实例、design_json 冗余 subDoc、advance_mode 冗余）→ WAIT 停止等待/IMMEDIATE 继续 advance(childNode)；completeSubInstance 按 advance_mode 分叉回推；cascadeTreeTerminal 按 root_instance_id 整树级联（不区分模式）
- **锁调整**：推进锁 key 改 {rootInstanceId}（防父↔子死锁）；发起 40049 判定改根实例口径；loadDesign 双口径（根读版本表、子读冗余列）
- **发布校验递归化**：ProcessDesignValidator 抽出，subDoc 递归全部既有规则+深度/摆放/模式校验，错误带「子流程【X】内：」前缀
- **性能与可靠性**：子实例判定读自身 advance_mode 列（无回溯）；idx_root(root_instance_id,status) 覆盖级联查询；树级锁串行化整树流转，临界区短；旧快照缺省 advanceMode=WAIT 兼容，既有路径零改动
- **测试**：引擎纯逻辑单测（两种模式全分叉）+ 旧数据回归

## Directory Structure（设计文档 §5，全部已确认存在）
```
backend/
├── sql/06-sub-process-schema.sql                                          # [NEW] ALTER flow_instance 增 6 列+2 索引+存量回填
├── ocr-common/.../common/ResultCode.java                                  # [MODIFY] 追加 40065~40068
├── ocr-dal/.../mapper/entity/FlowInstanceDO.java                          # [MODIFY] 增 6 字段
└── ocr-service/.../service/approval/
    ├── engine/FlowEngine.java                                             # [MODIFY] 接口+buildSubInstance/completeSubInstance/cascadeTreeTerminal
    ├── engine/FlowEngineImpl.java                                         # [MODIFY] advance 分支/三个新方法/树级锁/预览穿透
    ├── engine/FlowEngineSupport.java                                      # [MODIFY] loadDesign 双口径+SUB_PROCESS 常量
    ├── engine/EngineCtx.java                                              # [MODIFY] 增 rootInstanceId/depth/parentInstanceId
    ├── ProcessDesignValidator.java                                        # [NEW] 发布校验递归化（自 ProcessServiceImpl 抽出）
    ├── impl/ProcessServiceImpl.java                                       # [MODIFY] 接入校验器
    ├── impl/FlowInstanceServiceImpl.java                                  # [MODIFY] 发起/撤销/列表根实例口径、40068
    ├── impl/FlowTaskServiceImpl.java                                      # [MODIFY] VO 组装补 subProcessName
    ├── impl/InstanceDetailAssembler.java                                  # [MODIFY] subInstances 装配+树级可见性
    └── model/vo/SubInstanceSummaryVO.java 等                              # [NEW/MODIFY] 详情/任务 VO 扩展
└── ocr-web/src/main/resources/application.yml                             # [MODIFY] approval.sub-process.max-depth=3

frontend/src/
├── types/workflow.ts                                                      # [MODIFY] NodeType/SubProcessProps/createSubDoc/MAX_SUB_PROCESS_DEPTH
├── types/approval.ts                                                      # [MODIFY] TaskVO/InstanceDetailVO 字段
├── stores/workflow.ts                                                     # [MODIFY] 设计视图栈 designStack
├── composables/useWorkflowValidate.ts                                     # [MODIFY] 递归校验 subDoc
├── components/workflow/{NodeCard,AddNodeButton,NodePropDrawer,WorkflowToolbar,NodeChain,WorkflowCanvas}.vue  # [MODIFY] 钻入/徽标/单选/面包屑/currentDoc
└── views/system/WorkflowDesignView.vue、views/approval/{TodoTaskView,DoneTaskView,InstanceDetailView}.vue   # [MODIFY] 绑定视图栈/徽标/子实例卡片
```

## Implementation Notes
- FlowEngineImpl（约 40KB）为核心改动，仅新增分支与方法，不动既有节点处理路径；改动前先通读 advance/链尾判定/REJECT 终止/加锁入口四处现状
- 树级锁切换：Service 层所有锁获取入口统一改为按实例行解析 rootInstanceId 后加锁
- 审批记录遵循「每次操作仅一条 flow_task_log」：进入子流程/子流程完成/旁路拒绝等系统说明均聚合进触发记录
- 前端：保存/发布仍提交整个主文档（subDoc 内嵌），无单独保存入口；子视图 START 隐藏发起范围配置

## Agent Extensions
### Skill
- **java-p3c-generate**
  - Purpose: 按 P3C 规约生成/修改后端 Java 代码（DDL 脚本、DO/VO/ResultCode、ProcessDesignValidator、引擎三方法、Service 适配）
  - Expected outcome: 后端 M1/M2a/M3/M4 代码符合项目多模块规约，编译通过
- **frontend-generate**
  - Purpose: 按项目前端规约生成/修改 Vue3+TS 代码（类型、store 视图栈、设计器组件、运行态页面）
  - Expected outcome: 前端 M2b/M5 代码符合分层与 TS strict 约定，类型对齐后端契约
