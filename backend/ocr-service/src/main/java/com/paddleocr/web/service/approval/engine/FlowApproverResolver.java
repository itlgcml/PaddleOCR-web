package com.paddleocr.web.service.approval.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.paddleocr.web.mapper.RoleMapper;
import com.paddleocr.web.mapper.UserMapper;
import com.paddleocr.web.mapper.UserRoleMapper;
import com.paddleocr.web.mapper.entity.RoleDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.mapper.entity.UserRoleDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批人解析器：ApproverProps（source/memberIds/roleIds/emptyPolicy）→ 启用用户集合
 *
 * <p>降级语义（V1）：LEADER/SELF_SELECT 来源运行时视为空 + ENGINE 日志说明；
 * TO_LEADER 空审批人策略降级为 TO_ADMIN；TO_ADMIN 仍无可用管理员时按空处理（AUTO_PASS）。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowApproverResolver {

    private static final String SOURCE_MEMBER = "MEMBER";
    private static final String SOURCE_INITIATOR = "INITIATOR";
    private static final String SOURCE_LEADER = "LEADER";
    private static final String SOURCE_SELF_SELECT = "SELF_SELECT";

    private static final String POLICY_AUTO_PASS = "AUTO_PASS";
    private static final String POLICY_TO_LEADER = "TO_LEADER";
    private static final String POLICY_TO_ADMIN = "TO_ADMIN";

    private static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    /**
     * 解析结果：启用用户列表 + 引擎说明（降级/兜底过程，写 ENGINE 日志可审计）
     */
    public static final class Resolved {

        private final List<UserDO> users;
        private final List<String> engineNotes;

        public Resolved(List<UserDO> users, List<String> engineNotes) {
            this.users = users;
            this.engineNotes = engineNotes;
        }

        public List<UserDO> getUsers() {
            return users;
        }

        public List<String> getEngineNotes() {
            return engineNotes;
        }
    }

    /**
     * 解析审批节点审批人
     *
     * @param approverNode     审批节点（type=APPROVER）
     * @param initiatorUserId  发起人 ID（INITIATOR 来源代入）
     * @return 解析结果（可能为空集合，由调用方按 emptyPolicy 分支处理）
     */
    public Resolved resolve(JsonNode approverNode, Long initiatorUserId) {
        List<String> notes = new ArrayList<>();
        JsonNode props = FlowDesignCursor.propsOf(approverNode);
        String source = props.path("source").asText(SOURCE_MEMBER);
        String emptyPolicy = props.path("emptyPolicy").asText(POLICY_AUTO_PASS);

        List<UserDO> users = Collections.emptyList();
        switch (source) {
            case SOURCE_MEMBER:
                users = expandUsers(props.path("memberIds"), props.path("roleIds"));
                break;
            case SOURCE_INITIATOR:
                users = loadEnabledUsers(Collections.singletonList(initiatorUserId));
                break;
            case SOURCE_LEADER:
                notes.add("LEADER 主管数据未接入，按空审批人策略处理");
                break;
            case SOURCE_SELF_SELECT:
                notes.add("SELF_SELECT 已取消人工指定，按空审批人策略处理");
                break;
            default:
                notes.add("未知审批人来源 source=" + source + "，按空审批人策略处理");
                break;
        }

        if (!users.isEmpty()) {
            return new Resolved(users, notes);
        }

        // 空审批人策略
        if (POLICY_AUTO_PASS.equals(emptyPolicy)) {
            return new Resolved(Collections.emptyList(), notes);
        }
        if (POLICY_TO_LEADER.equals(emptyPolicy)) {
            notes.add("TO_LEADER 主管数据未接入，降级为 TO_ADMIN");
        }
        List<UserDO> admins = listAdmins();
        if (admins.isEmpty()) {
            notes.add("无可用 ADMIN 用户，按自动通过处理");
            return new Resolved(Collections.emptyList(), notes);
        }
        return new Resolved(admins, notes);
    }

    /**
     * 发起范围校验用：memberIds ∪ roleIds 展开为启用用户（去重、按选择顺序）
     */
    public List<UserDO> expandUsers(JsonNode memberIds, JsonNode roleIds) {
        Set<Long> userIds = new LinkedHashSet<>();
        readIds(memberIds, userIds);
        Set<Long> roleSet = new LinkedHashSet<>();
        readIds(roleIds, roleSet);
        if (!roleSet.isEmpty()) {
            for (UserRoleDO userRole : listUserRoles(roleSet)) {
                userIds.add(userRole.getUserId());
            }
        }
        return loadEnabledUsers(userIds);
    }

    /**
     * ADMIN 角色的启用用户
     */
    public List<UserDO> listAdmins() {
        RoleDO adminRole = roleMapper.selectOne(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getRoleCode, ROLE_ADMIN)
                .eq(RoleDO::getStatus, 1)
                .last("LIMIT 1"));
        if (adminRole == null) {
            return Collections.emptyList();
        }
        Set<Long> userIds = listUserRoles(Collections.singleton(adminRole.getId())).stream()
                .map(UserRoleDO::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return loadEnabledUsers(userIds);
    }

    /**
     * 启用用户显示名：昵称优先，缺省用户名
     */
    public static String displayName(UserDO user) {
        if (user == null) {
            return "";
        }
        String nickname = user.getNickname();
        return nickname == null || nickname.trim().isEmpty() ? user.getUsername() : nickname.trim();
    }

    private List<UserRoleDO> listUserRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleDO>()
                .in(UserRoleDO::getRoleId, roleIds));
    }

    private List<UserDO> loadEnabledUsers(Iterable<Long> userIds) {
        Set<Long> distinct = new LinkedHashSet<>();
        userIds.forEach(distinct::add);
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDO> users = userMapper.selectBatchIds(distinct).stream()
                .filter(user -> Integer.valueOf(1).equals(user.getStatus()))
                .collect(Collectors.toList());
        // selectBatchIds 不保序：按传入顺序重排（ LinkedHashMap 保持插入序 ）
        Map<Long, UserDO> byId = users.stream()
                .collect(Collectors.toMap(UserDO::getId, u -> u, (a, b) -> a, LinkedHashMap::new));
        List<UserDO> ordered = new ArrayList<>(users.size());
        for (Long id : distinct) {
            UserDO user = byId.get(id);
            if (user != null) {
                ordered.add(user);
            }
        }
        return ordered;
    }

    private void readIds(JsonNode array, Set<Long> target) {
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                try {
                    target.add(Long.parseLong(item.asText()));
                } catch (NumberFormatException e) {
                    log.warn("忽略非法人员/角色 ID: {}", item.asText());
                }
            }
        }
    }
}
