package com.paddleocr.web.service.approval.engine;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程设计快照游标：WorkflowDoc（root + childNode 主干链 + conditionNodes 分支列）的结构访问与条件求值
 *
 * <p>只认 Jackson JsonNode，不映射 POJO；节点结构契约与前端 src/types/workflow.ts 逐字段对齐。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public final class FlowDesignCursor {

    /** 节点类型常量（与前端 NodeType 枚举一致） */
    public static final String TYPE_START = "START";
    public static final String TYPE_APPROVER = "APPROVER";
    public static final String TYPE_CONDITIONS = "CONDITIONS";
    public static final String TYPE_PARALLEL = "PARALLEL";
    public static final String TYPE_TRANSACT = "TRANSACT";
    public static final String TYPE_SUB_PROCESS = "SUB_PROCESS";

    /** 条件分支路由模式：人工（上游审批人选择下一步，分支列以 label 表达选项） */
    public static final String ROUTING_MANUAL = "MANUAL";

    /** 流程根节点（START） */
    private final JsonNode root;

    private FlowDesignCursor(JsonNode root) {
        this.root = root;
    }

    /**
     * 从 WorkflowDoc JSON 构建游标
     */
    public static FlowDesignCursor of(JsonNode doc) {
        return new FlowDesignCursor(doc.path("root"));
    }

    /**
     * 流程根节点（START）
     */
    public JsonNode root() {
        return root;
    }

    /**
     * 节点 ID
     */
    public static String idOf(JsonNode node) {
        return node.path("id").asText("");
    }

    /**
     * 节点名称
     */
    public static String nameOf(JsonNode node) {
        return node.path("name").asText("");
    }

    /**
     * 节点类型
     */
    public static String typeOf(JsonNode node) {
        return node.path("type").asText("");
    }

    /**
     * 节点属性
     */
    public static JsonNode propsOf(JsonNode node) {
        return node.path("props");
    }

    /**
     * 主干链后继（START/TRANSACT/APPROVER/CONDITION/PARALLEL_BRANCH 通用；null 表示链尾）
     */
    public static JsonNode chainNext(JsonNode node) {
        JsonNode next = node.path("childNode");
        return next.isObject() ? next : null;
    }

    /**
     * 分支列内首个审批节点：沿列链向下跳过直通办理节点（TRANSACT，引擎推进时不建任务）；
     * 遇嵌套分支容器（CONDITIONS/PARALLEL）即返回 null——后续走向由容器决定，审批人不唯一。
     * 人工路由指定审批人的候选解析与引擎登记共用本口径。
     */
    public static JsonNode firstApproverInBranch(JsonNode column) {
        JsonNode node = chainNext(column);
        while (node != null) {
            String type = typeOf(node);
            if (TYPE_APPROVER.equals(type)) {
                return node;
            }
            if (TYPE_TRANSACT.equals(type)) {
                node = chainNext(node);
                continue;
            }
            return null;
        }
        return null;
    }

    /**
     * 人工路由指定审批人的目标定位：优先取分支列内首个审批节点；列内无审批节点
     * （空列或仅直通办理节点，引擎推进将汇合到容器后继链）时回退取容器后继链上的
     * 首个审批节点，与 advanceManualRoute/advanceManualParallel 对空列的流转落点一致。
     * 两侧定位口径相同（跳过直通办理节点、遇嵌套分支容器即 null——后续走向由容器决定，
     * 审批人不唯一）。人工路由的候选预览与引擎登记覆盖共用本口径。
     */
    public static JsonNode routeAssigneeTarget(JsonNode column, JsonNode container) {
        JsonNode inBranch = firstApproverInBranch(column);
        return inBranch != null ? inBranch : firstApproverInBranch(container);
    }

    /**
     * 链后继穿透落点（「下一节点审批人」指定口径）：从 from 的链后继出发跳过直通办理节点
     * （TRANSACT，引擎推进不建任务）；from 为分支列尾（childNode 空）时逐层向外汇合——
     * 嵌套分支取宿主容器后继继续（与引擎 tryMergeParallel 完成容器后 advance 容器 childNode
     * 的流转落点一致，途经容器收集入 passedHosts 供下发侧汇合判定，可传 null 不收集）；
     * 遇审批节点（APPROVER）返回；遇分支容器/子流程（后续走向由容器或子流程决定，审批人
     * 不唯一）或主干链尾（无后续任务）返回 null。任务详情 nextApproverOptions 下发与引擎
     * registerNextAssignees 登记共用本口径。
     */
    public JsonNode nextApproverTarget(JsonNode from, List<JsonNode> passedHosts) {
        JsonNode node = from;
        while (node != null) {
            JsonNode next = chainNext(node);
            while (next != null && TYPE_TRANSACT.equals(typeOf(next))) {
                next = chainNext(next);
            }
            if (next != null) {
                return TYPE_APPROVER.equals(typeOf(next)) ? next : null;
            }
            BranchHost host = findBranchHost(idOf(node));
            if (host == null) {
                return null;
            }
            if (passedHosts != null) {
                passedHosts.add(host.getContainer());
            }
            node = host.getContainer();
        }
        return null;
    }

    /**
     * 是否分支容器（CONDITIONS/PARALLEL）：两者都以 conditionNodes 横向展开多列
     */
    public static boolean isBranchContainer(JsonNode node) {
        String type = typeOf(node);
        return TYPE_CONDITIONS.equals(type) || TYPE_PARALLEL.equals(type);
    }

    /**
     * 是否人工路由节点（CONDITIONS 且 routing=MANUAL）：由上游审批人在 PASS 时携带 routeTargetId 选择分支列，
     * 引擎不做自动求值。旧数据缺省 routing 按 AUTO 处理。
     */
    public static boolean isManualRouting(JsonNode node) {
        return node != null
                && TYPE_CONDITIONS.equals(typeOf(node))
                && ROUTING_MANUAL.equals(propsOf(node).path("routing").asText("AUTO"));
    }

    /**
     * 是否人工并行节点（PARALLEL 且 routing=MANUAL）：由上游审批人在 PASS 时携带 routeTargetIds 勾选
     * 要执行的分支列集合（多选，至少一个），引擎只对选中列并行推进；AUTO（缺省）= 全部分支无条件并行。
     */
    public static boolean isManualParallel(JsonNode node) {
        return node != null
                && TYPE_PARALLEL.equals(typeOf(node))
                && ROUTING_MANUAL.equals(propsOf(node).path("routing").asText("AUTO"));
    }

    /**
     * 是否分支列（CONDITION/PARALLEL_BRANCH）
     */
    public static boolean isBranchColumn(JsonNode node) {
        String type = typeOf(node);
        return "CONDITION".equals(type) || "PARALLEL_BRANCH".equals(type);
    }

    /**
     * 容器的分支列数组（空安全）
     */
    public static List<JsonNode> branchColumns(JsonNode container) {
        List<JsonNode> columns = new ArrayList<>();
        JsonNode array = container.path("conditionNodes");
        if (array.isArray()) {
            array.forEach(columns::add);
        }
        return columns;
    }

    /**
     * 先序遍历全树节点（root → 各分支列链 → 容器后继；分支列先于容器后继）
     */
    public List<JsonNode> preorder() {
        List<JsonNode> order = new ArrayList<>();
        collectPreorder(root, order);
        return order;
    }

    private void collectPreorder(JsonNode node, List<JsonNode> order) {
        JsonNode cursor = node;
        while (cursor != null) {
            order.add(cursor);
            if (isBranchContainer(cursor)) {
                for (JsonNode column : branchColumns(cursor)) {
                    collectPreorder(column, order);
                }
            }
            cursor = chainNext(cursor);
        }
    }

    /**
     * 按 ID 查找节点（先序全树）；未找到返回 null
     */
    public JsonNode findNode(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return null;
        }
        for (JsonNode node : preorder()) {
            if (nodeId.equals(idOf(node))) {
                return node;
            }
        }
        return null;
    }

    /**
     * 退回目标集合（静态设计口径）：从根到 nodeId 的唯一路径上先于它的 START/APPROVER 节点
     * （对齐前端 getRejectTargets 语义）。路径外分支列内的节点不算上游——并行兄弟分支与
     * 未选条件列不可作为退回目标；nodeId 不在树内返回空集
     */
    public List<JsonNode> rejectTargetsBefore(String nodeId) {
        return rejectTargetsBefore(nodeId, null);
    }

    /**
     * 退回目标集合（运行时口径）：静态路径目标之上，补入实例内已办结（passedNodeIds，含
     * AUTO_PASS 伪任务与退回重走的历史任务）且位于当前节点上游分支列内的 APPROVER——
     * 条件路由已走过的列内节点是事实上游，而静态路径不进分支列（分支容器后继主干上的
     * 节点其 trail 不含任何条件列），不补则「分支后节点退回已走过的列内审批节点」永远缺失。
     * 当前节点处于某分支列内时，同宿主容器的其它列（并行兄弟分支）不补（对齐 2026-10-26 口径）。
     * 结果统一按设计先序排序（目标下拉按流程方向展示）
     */
    public List<JsonNode> rejectTargetsBefore(String nodeId, Set<String> passedNodeIds) {
        List<JsonNode> targets = new ArrayList<>();
        List<JsonNode> trail = new ArrayList<>();
        if (!findTrail(root, nodeId, trail)) {
            return targets;
        }
        Set<String> trailIds = new LinkedHashSet<>();
        for (JsonNode node : trail) {
            trailIds.add(idOf(node));
        }
        // 当前节点所处最内层分支容器：其内其它列（并行兄弟分支）不算上游
        BranchHost currentHost = findBranchHost(nodeId);
        String currentHostId = currentHost == null ? null : idOf(currentHost.getContainer());
        Set<String> collected = new LinkedHashSet<>();
        for (JsonNode node : trail) {
            if (nodeId.equals(idOf(node))) {
                break;
            }
            String type = typeOf(node);
            if (TYPE_START.equals(type) || TYPE_APPROVER.equals(type)) {
                targets.add(node);
                collected.add(idOf(node));
            }
        }
        if (passedNodeIds != null && !passedNodeIds.isEmpty()) {
            for (String passedId : passedNodeIds) {
                if (passedId == null || passedId.isEmpty()
                        || passedId.equals(nodeId) || collected.contains(passedId)) {
                    continue;
                }
                JsonNode passed = findNode(passedId);
                if (passed == null || !TYPE_APPROVER.equals(typeOf(passed))) {
                    continue;
                }
                if (isPassedBranchUpstream(passedId, trailIds, currentHostId)) {
                    targets.add(passed);
                    collected.add(passedId);
                }
            }
        }
        if (targets.size() > 1) {
            Map<String, Integer> order = new HashMap<>();
            List<JsonNode> sequence = preorder();
            for (int i = 0; i < sequence.size(); i++) {
                order.put(idOf(sequence.get(i)), i);
            }
            targets.sort(Comparator.comparingInt(node -> order.getOrDefault(idOf(node), Integer.MAX_VALUE)));
        }
        return targets;
    }

    /**
     * 已办结节点是否位于当前节点上游分支列内：自内向外沿宿主容器链找首个位于静态路径
     * （trail）上的容器，且该容器不是当前节点自身所处容器（并行兄弟分支不算上游）；
     * 宿主链穷尽仍不在路径上（主干或旁支节点）返回 false
     */
    private boolean isPassedBranchUpstream(String passedNodeId, Set<String> trailIds, String currentHostId) {
        String cursorId = passedNodeId;
        while (cursorId != null && !cursorId.isEmpty()) {
            BranchHost host = findBranchHost(cursorId);
            if (host == null) {
                return false;
            }
            String containerId = idOf(host.getContainer());
            if (trailIds.contains(containerId)) {
                return !containerId.equals(currentHostId);
            }
            cursorId = containerId;
        }
        return false;
    }

    /**
     * 先序查找从 node 到目标节点的路径（含目标自身）：先沿主干链后继深入，再进各分支列，
     * 未命中回溯弹出（非路径节点不保留）；命中返回 true 且 trail 即路径
     */
    private boolean findTrail(JsonNode node, String nodeId, List<JsonNode> trail) {
        if (node == null) {
            return false;
        }
        trail.add(node);
        if (nodeId.equals(idOf(node))) {
            return true;
        }
        if (findTrail(chainNext(node), nodeId, trail)) {
            return true;
        }
        if (isBranchContainer(node)) {
            for (JsonNode column : branchColumns(node)) {
                if (findTrail(column, nodeId, trail)) {
                    return true;
                }
            }
        }
        trail.remove(trail.size() - 1);
        return false;
    }

    /**
     * 分支列链上的全部节点 ID（含分支列自身、链上节点及嵌套容器的全部分支列链，递归）
     */
    public Set<String> collectBranchNodeIds(JsonNode branchColumn) {
        Set<String> ids = new LinkedHashSet<>();
        JsonNode cursor = branchColumn;
        while (cursor != null) {
            ids.add(idOf(cursor));
            if (isBranchContainer(cursor)) {
                for (JsonNode column : branchColumns(cursor)) {
                    ids.addAll(collectBranchNodeIds(column));
                }
            }
            cursor = chainNext(cursor);
        }
        return ids;
    }

    /**
     * 节点所属分支宿主（含嵌套时返回最内层）
     *
     * @return 宿主（container + branchColumn）；节点在主干上返回 null
     */
    public BranchHost findBranchHost(String nodeId) {
        BranchHost best = null;
        for (JsonNode container : preorder()) {
            if (!isBranchContainer(container)) {
                continue;
            }
            for (JsonNode column : branchColumns(container)) {
                Set<String> ids = collectBranchNodeIds(column);
                if (ids.contains(nodeId) && (best == null || ids.size() < best.size())) {
                    best = new BranchHost(container, column, ids);
                }
            }
        }
        return best;
    }

    /**
     * CONDITIONS 容器选路：非默认列按 priority 升序逐列求值（组内组间 AND/OR 折叠），
     * 全不命中走默认列（isDefault），无默认列返回 null（直通容器后继）
     *
     * @param values 条件值（fieldKey → 值）；运行时=表单快照，预览=conditions Map；缺失键的规则一律不满足
     */
    public JsonNode selectConditionBranch(JsonNode container, Map<String, String> values) {
        List<JsonNode> columns = branchColumns(container);
        List<JsonNode> sorted = new ArrayList<>();
        JsonNode defaultColumn = null;
        for (JsonNode column : columns) {
            JsonNode props = propsOf(column);
            if (props.path("isDefault").asBoolean(false)) {
                defaultColumn = column;
            } else {
                sorted.add(column);
            }
        }
        sorted.sort(Comparator.comparingInt(c -> propsOf(c).path("priority").asInt(0)));
        Map<String, String> safeValues = values == null ? new LinkedHashMap<>() : values;
        for (JsonNode column : sorted) {
            JsonNode groups = propsOf(column).path("groups");
            if (!groups.isArray() || groups.isEmpty()) {
                continue;
            }
            if (evaluateGroups(groups, safeValues)) {
                return column;
            }
        }
        return defaultColumn;
    }

    /**
     * 条件组间折叠：组间按各组的 link 串联（首组忽略）
     */
    private boolean evaluateGroups(JsonNode groups, Map<String, String> values) {
        Boolean result = null;
        for (JsonNode group : groups) {
            boolean groupResult = evaluateGroup(group, values);
            if (result == null) {
                result = groupResult;
            } else {
                boolean and = "AND".equals(group.path("link").asText("AND"));
                result = and ? result && groupResult : result || groupResult;
            }
        }
        return Boolean.TRUE.equals(result);
    }

    /**
     * 条件组内折叠：规则按各自 link 串联（首条忽略）
     */
    private boolean evaluateGroup(JsonNode group, Map<String, String> values) {
        JsonNode rules = group.path("rules");
        if (!rules.isArray() || rules.isEmpty()) {
            return false;
        }
        Boolean result = null;
        for (JsonNode rule : rules) {
            boolean ruleResult = evaluateRule(rule, values);
            if (result == null) {
                result = ruleResult;
            } else {
                boolean and = "AND".equals(rule.path("link").asText("AND"));
                result = and ? result && ruleResult : result || ruleResult;
            }
        }
        return Boolean.TRUE.equals(result);
    }

    /**
     * 单条规则求值：值缺失一律不满足；EQ/NEQ 数值-字符串自适应，GT/GTE/LT/LTE 仅数值比较，
     * CONTAIN/NOT_CONTAIN 字符串包含
     */
    private boolean evaluateRule(JsonNode rule, Map<String, String> values) {
        String fieldKey = rule.path("fieldKey").asText("");
        String operator = rule.path("operator").asText("EQ");
        String expected = rule.path("value").asText("");
        String actual = values.get(fieldKey);
        if (actual == null) {
            return false;
        }
        switch (operator) {
            case "EQ":
                return compareEquals(actual, expected) == 0;
            case "NEQ":
                return compareEquals(actual, expected) != 0;
            case "GT": {
                Integer cmp = compareOrderly(actual, expected);
                return cmp != null && cmp > 0;
            }
            case "GTE": {
                Integer cmp = compareOrderly(actual, expected);
                return cmp != null && cmp >= 0;
            }
            case "LT": {
                Integer cmp = compareOrderly(actual, expected);
                return cmp != null && cmp < 0;
            }
            case "LTE": {
                Integer cmp = compareOrderly(actual, expected);
                return cmp != null && cmp <= 0;
            }
            case "CONTAIN":
                return actual.contains(expected);
            case "NOT_CONTAIN":
                return !actual.contains(expected);
            default:
                return false;
        }
    }

    /**
     * 等值比较：双方可解析为数值则数值比较，否则字符串比较
     */
    private int compareEquals(String left, String right) {
        BigDecimal leftNum = tryParseDecimal(left);
        BigDecimal rightNum = tryParseDecimal(right);
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum);
        }
        return left.trim().compareTo(right.trim());
    }

    /**
     * 大小比较：仅双方均可解析为数值时返回比较结果；任一方不可解析返回 null（该规则不满足）
     */
    private Integer compareOrderly(String left, String right) {
        BigDecimal leftNum = tryParseDecimal(left);
        BigDecimal rightNum = tryParseDecimal(right);
        if (leftNum == null || rightNum == null) {
            return null;
        }
        return leftNum.compareTo(rightNum);
    }

    private BigDecimal tryParseDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 分支宿主：容器 + 分支列 + 分支链节点集合（汇合判定用）
     */
    public static final class BranchHost {

        private final JsonNode container;
        private final JsonNode branchColumn;
        private final Set<String> branchNodeIds;

        private BranchHost(JsonNode container, JsonNode branchColumn, Set<String> branchNodeIds) {
            this.container = container;
            this.branchColumn = branchColumn;
            this.branchNodeIds = branchNodeIds;
        }

        public JsonNode getContainer() {
            return container;
        }

        public JsonNode getBranchColumn() {
            return branchColumn;
        }

        public Set<String> getBranchNodeIds() {
            return branchNodeIds;
        }

        public int size() {
            return branchNodeIds.size();
        }
    }
}
