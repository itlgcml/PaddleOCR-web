package com.paddleocr.web.service.approval;

import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.ProcessDesignSaveDTO;
import com.paddleocr.web.model.dto.ProcessPageQuery;
import com.paddleocr.web.model.dto.ProcessSaveDTO;
import com.paddleocr.web.model.dto.ProcessUpdateDTO;
import com.paddleocr.web.model.vo.ProcessDesignVO;
import com.paddleocr.web.model.vo.ProcessDetailVO;
import com.paddleocr.web.model.vo.ProcessVersionVO;
import com.paddleocr.web.model.vo.ProcessVO;

import java.util.List;

/**
 * 流程配置服务（审批域）：流程定义 CRUD、流程设计草稿持久化、版本管理、状态机流转
 *
 * @author paddleocr
 * @date 2026-09-30
 */
public interface ProcessService {

    /**
     * 分页查询流程配置
     *
     * @param query 分页参数（keyword 前缀匹配 code/name，status 精确过滤）
     * @return 分页结果
     */
    PageResultVO<ProcessVO> page(ProcessPageQuery query);

    /**
     * 查询流程详情（基本信息 + 发起范围数组 + 当前版本号）
     *
     * @param id 流程定义 ID
     * @return 详情
     * @throws com.paddleocr.web.common.BusinessException 40041 流程不存在
     */
    ProcessDetailVO detail(Long id);

    /**
     * 新增流程（仅基本信息，初始 DRAFT，无版本无草稿）
     *
     * @param dto 新增入参
     * @return 新流程 ID（字符串形式，防前端精度丢失）
     * @throws com.paddleocr.web.common.BusinessException 40040 流程编码已存在
     */
    String create(ProcessSaveDTO dto);

    /**
     * 更新基本信息（仅 DRAFT 态，code 不可改）
     *
     * @param id 流程定义 ID
     * @param dto 更新入参
     * @throws com.paddleocr.web.common.BusinessException 40041 不存在；40043 非 DRAFT 态
     */
    void update(Long id, ProcessUpdateDTO dto);

    /**
     * 保存流程设计草稿（任意未删除状态均可，仅覆盖 draft_json，不产生新版本、不影响已发布快照）
     *
     * @param id 流程定义 ID
     * @param dto 设计 JSON 入参
     * @throws com.paddleocr.web.common.BusinessException 40041 不存在；40042 JSON 不可解析或结构不合法；40045 重复提交
     */
    void saveDesign(Long id, ProcessDesignSaveDTO dto);

    /**
     * 查询流程设计（缺省返回草稿，可指定已发布版本）
     *
     * @param id 流程定义 ID
     * @param version 版本号，null/空白返回草稿
     * @return 设计内容
     * @throws com.paddleocr.web.common.BusinessException 40041 不存在；40044 指定版本不存在
     */
    ProcessDesignVO getDesign(Long id, String version);

    /**
     * 发布流程（校验草稿 → 生成新版本快照 v{n+1} → 状态置 PUBLISHED）
     *
     * @param id 流程定义 ID
     * @return 本次发布的版本号，如 v2
     * @throws com.paddleocr.web.common.BusinessException 40041 不存在；40042 设计不合法；40044 无可发布草稿；40045 重复提交
     */
    String publish(Long id);

    /**
     * 停用流程（仅 PUBLISHED 态）
     *
     * @param id 流程定义 ID
     * @throws com.paddleocr.web.common.BusinessException 40041 不存在；40043 非 PUBLISHED 态
     */
    void disable(Long id);

    /**
     * 查询版本列表（按发布时间倒序）
     *
     * @param id 流程定义 ID
     * @return 版本列表
     * @throws com.paddleocr.web.common.BusinessException 40041 不存在
     */
    List<ProcessVersionVO> versions(Long id);

    /**
     * 逻辑删除流程（仅 DRAFT 态，连带逻辑删除其版本记录）
     *
     * @param id 流程定义 ID
     * @throws com.paddleocr.web.common.BusinessException 40041 不存在；40043 非 DRAFT 态
     */
    void remove(Long id);
}
