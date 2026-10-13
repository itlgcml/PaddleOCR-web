package com.paddleocr.web.service.approval.engine;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
     * 是否分支容器（CONDITIONS/PARALLEL）：两者都以 conditionNodes 横向展开多列
     */
    public static boolean isBranchContainer(JsonNode node) {
        String type = typeOf(node);
        return TYPE_CONDITIONS.equals(type) || TYPE_PARALLEL.equals(type);
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
     * 退回目标集合：先序位置在 nodeId 之前的 START/APPROVER 节点（对齐前端 getRejectTargets 语义）
     */
    public List<JsonNode> rejectTargetsBefore(String nodeId) {
        List<JsonNode> targets = new ArrayList<>();
        for (JsonNode node : preorder()) {
            if (nodeId.equals(idOf(node))) {
                break;
            }
            String type = typeOf(node);
            if (TYPE_START.equals(type) || TYPE_APPROVER.equals(type)) {
                targets.add(node);
            }
        }
        return targets;
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
