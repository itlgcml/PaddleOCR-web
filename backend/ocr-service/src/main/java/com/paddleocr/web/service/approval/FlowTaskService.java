package com.paddleocr.web.service.approval;

import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.dto.TaskActionDTO;
import com.paddleocr.web.model.vo.TaskDetailVO;
import com.paddleocr.web.model.vo.TaskVO;

/**
 * 审批任务服务（待办/已办/办理视图/统一动作）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public interface FlowTaskService {

    /**
     * 我的待办分页（PENDING，keyword 对业务标题/流程名称过滤）
     */
    PageResultVO<TaskVO> pageTodo(RuntimePageQuery query, Long userId);

    /**
     * 我的已办分页（PASSED/REJECTED/TRANSFERRED，action 过滤）
     */
    PageResultVO<TaskVO> pageDone(RuntimePageQuery query, Long userId);

    /**
     * 任务办理视图（实例详情全量 + 任务本体 + 按钮配置 + 退回目标 + 可操作性）；非参与人 40059
     */
    TaskDetailVO detail(Long taskId, Long userId);

    /**
     * 统一动作入口（与前端 ApprovalActionPayload 一一对应，引擎锁内处理）
     */
    void action(Long taskId, TaskActionDTO dto, Long userId);
}
