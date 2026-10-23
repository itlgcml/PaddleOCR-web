package com.paddleocr.web.service.approval;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 流程设计发布校验器（自 ProcessServiceImpl 内嵌校验逻辑抽出）：
 * 对整棵设计执行最小结构校验，规则与前端 useWorkflowValidate 对齐；SUB_PROCESS 节点内嵌
 * subDoc 递归执行同一套规则（错误信息带「子流程【X】内：」前缀链）。
 *
 * <p>错误码归类：主 doc（depth=0）既有规则失败 → 40042；子 doc 内任何既有规则失败 → 40065；
 * subDoc 结构缺失/推进方式非法 → 40065；嵌套深度超限 → 40066；分支列内摆放 SUB_PROCESS → 40067。</p>
 *
 * @author paddleocr
 * @date 2026-10-23
 */
@Component
@RequiredArgsConstructor
public class ProcessDesignValidator {

    /** 节点类型：发起人 */
    private static final String TYPE_START = "START";

    /** 节点类型：审批人 */
    private static final String TYPE_APPROVER = "APPROVER";

    /** 节点类型：条件分支列 */
    private static final String TYPE_CONDITION = "CONDITION";

    /** 节点类型：条件分支容器 */
    private static final String TYPE_CONDITIONS = "CONDITIONS";

    /** 节点类型：并行节点容器 */
    private static final String TYPE_PARALLEL = "PARALLEL";

    /** 节点类型：子流程 */
    private static final String TYPE_SUB_PROCESS = "SUB_PROCESS";

    /** 条件分支路由模式：自动（属性缺省值） */
    private static final String ROUTING_AUTO = "AUTO";

    /** 条件分支路由模式：人工（上游审批人在 PASS 时选择下一步） */
    private static final String ROUTING_MANUAL = "MANUAL";

    /** 审批人来源-指定成员/角色 */
    private static final String APPROVER_SOURCE_MEMBER = "MEMBER";

    /** 推进模式：父实例等待子流程完成后继续（缺省值） */
    private static final String ADVANCE_MODE_WAIT = "WAIT";

    /** 推进模式：子流程启动后父实例立即继续 */
    private static final String ADVANCE_MODE_IMMEDIATE = "IMMEDIATE";

    private final ApprovalSubProcessProperties subProcessProperties;

    /**
     * 校验整棵设计（含 subDoc 递归），不通过即抛 BusinessException
     *
     * @param doc 设计文档（{name, updatedAt, root}，root 固定为 START；subDoc 结构同构）
     * @param depth 当前 doc 的嵌套深度（主 doc=0，子 doc≥1）
     */
    public void validateDoc(JsonNode doc, int depth) {
        validateDoc(doc, depth, "");
    }

    /**
     * 递归入口：先校验根节点契约，再先序遍历全树
     *
     * @param prefix 错误信息前缀链（如「子流程【X】内：」），主 doc 为空串
     */
    private void validateDoc(JsonNode doc, int depth, String prefix) {
        JsonNode root = doc.path("root");
        if (!root.isObject() || !TYPE_START.equals(root.path("type").asText())) {
            throw designError(depth, prefix + "流程根节点缺失或不是发起人节点");
        }
        validateNode(root, null, depth, prefix, false);
    }

    /**
     * 发布级校验（对齐前端 useWorkflowValidate）：先序遍历递归全树
     * <ul>
     *     <li>节点 name 非空</li>
     *     <li>APPROVER 且 source=MEMBER 时 memberIds/roleIds 并集非空</li>
     *     <li>CONDITIONS routing=MANUAL 时：至少 2 个选项、每列 label 非空</li>
     *     <li>PARALLEL routing=MANUAL 时：至少 2 个分支</li>
     *     <li>CONDITION 非 isDefault 且所属容器非人工路由时规则数为零则报错</li>
     *     <li>SUB_PROCESS：subDoc 结构合法、advanceMode 合法、深度受控、不得位于分支列内（40067）</li>
     * </ul>
     *
     * @param parentRouting 所属分支容器（CONDITIONS/PARALLEL）的路由模式，主干链首节点为 null
     * @param inBranchColumn 是否位于 conditionNodes 分支列内（SUB_PROCESS 摆放限制判定）
     */
    private void validateNode(JsonNode node, String parentRouting, int depth, String prefix, boolean inBranchColumn) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String name = node.path("name").asText();
        String type = node.path("type").asText();
        // 摆放限制先于其他规则：分支列内的 SUB_PROCESS 一律 40067
        if (TYPE_SUB_PROCESS.equals(type) && inBranchColumn) {
            throw new BusinessException(ResultCode.SUB_PROCESS_PLACEMENT_INVALID,
                    prefix + "子流程节点「" + name + "」不能放在分支列内");
        }
        if (StrUtil.isBlank(name)) {
            throw designError(depth, prefix + "存在未命名的节点");
        }
        JsonNode props = node.path("props");
        String routing = null;
        if (TYPE_CONDITIONS.equals(type)) {
            routing = props.path("routing").asText(ROUTING_AUTO);
            if (ROUTING_MANUAL.equals(routing)) {
                validateManualRouting(node, depth, prefix);
            }
        }
        if (TYPE_PARALLEL.equals(type)) {
            routing = props.path("routing").asText(ROUTING_AUTO);
            if (ROUTING_MANUAL.equals(routing)) {
                JsonNode columns = node.path("conditionNodes");
                if (!columns.isArray() || columns.size() < 2) {
                    throw designError(depth, prefix + "人工并行节点「" + name + "」至少需要 2 个分支");
                }
            }
        }
        if (TYPE_APPROVER.equals(type) && APPROVER_SOURCE_MEMBER.equals(props.path("source").asText())) {
            if (props.path("memberIds").size() + props.path("roleIds").size() == 0) {
                throw designError(depth, prefix + "审批节点「" + name + "」未选择审批人");
            }
        }
        if (TYPE_CONDITION.equals(type) && !props.path("isDefault").asBoolean(false)) {
            // 人工路由容器下的分支列以 label 表达选项，不做规则校验（label 已在 validateManualRouting 统一校验）
            if (!ROUTING_MANUAL.equals(parentRouting) && countConditionRules(props) == 0) {
                throw designError(depth, prefix + "条件「" + name + "」未配置规则");
            }
        }
        if (TYPE_SUB_PROCESS.equals(type)) {
            validateSubProcess(node, depth, prefix);
        }
        for (JsonNode branch : node.path("conditionNodes")) {
            validateNode(branch, routing, depth, prefix, true);
        }
        validateNode(node.path("childNode"), parentRouting, depth, prefix, false);
    }

    /**
     * SUB_PROCESS 节点专属校验：subDoc 结构、推进方式、嵌套深度，通过后递归校验子 doc
     */
    private void validateSubProcess(JsonNode node, int depth, String prefix) {
        String name = node.path("name").asText();
        JsonNode props = node.path("props");
        JsonNode subDoc = props.path("subDoc");
        if (!subDoc.isObject() || !subDoc.path("root").isObject()
                || !TYPE_START.equals(subDoc.path("root").path("type").asText())) {
            throw new BusinessException(ResultCode.SUB_PROCESS_DESIGN_INVALID,
                    prefix + "子流程「" + name + "」设计缺失或不合法");
        }
        // 缺省视为 WAIT（旧快照兼容），仅非法显式值报错
        String advanceMode = props.path("advanceMode").asText(ADVANCE_MODE_WAIT);
        if (!ADVANCE_MODE_WAIT.equals(advanceMode) && !ADVANCE_MODE_IMMEDIATE.equals(advanceMode)) {
            throw new BusinessException(ResultCode.SUB_PROCESS_DESIGN_INVALID,
                    prefix + "子流程「" + name + "」推进方式非法");
        }
        if (depth + 1 > subProcessProperties.getMaxDepth()) {
            throw new BusinessException(ResultCode.SUB_PROCESS_DEPTH_EXCEEDED,
                    prefix + "子流程「" + name + "」嵌套层数超过上限 " + subProcessProperties.getMaxDepth());
        }
        validateDoc(subDoc, depth + 1, prefix + "子流程【" + name + "】内：");
    }

    /**
     * 人工路由节点校验：至少 2 个选项，且每个分支列配置了选项名称（label）
     */
    private void validateManualRouting(JsonNode conditionsNode, int depth, String prefix) {
        JsonNode columns = conditionsNode.path("conditionNodes");
        if (!columns.isArray() || columns.size() < 2) {
            throw designError(depth, prefix + "人工路由节点「" + conditionsNode.path("name").asText() + "」至少需要 2 个选项");
        }
        for (JsonNode column : columns) {
            if (StrUtil.isBlank(column.path("props").path("label").asText())) {
                throw designError(depth, prefix + "人工路由节点「" + conditionsNode.path("name").asText() + "」存在未命名的选项");
            }
        }
    }

    /**
     * 统计条件属性内所有条件组的规则总数
     */
    private int countConditionRules(JsonNode props) {
        int count = 0;
        for (JsonNode group : props.path("groups")) {
            count += group.path("rules").size();
        }
        return count;
    }

    /**
     * 既有规则失败按 doc 深度归类错误码：主 doc=40042（保持既有前端契约），子 doc=40065
     */
    private BusinessException designError(int depth, String message) {
        return new BusinessException(
                depth == 0 ? ResultCode.PROCESS_DESIGN_INVALID : ResultCode.SUB_PROCESS_DESIGN_INVALID, message);
    }
}
