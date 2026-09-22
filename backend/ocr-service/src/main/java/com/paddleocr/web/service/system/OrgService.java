package com.paddleocr.web.service.system;

import com.paddleocr.web.model.dto.OrgSaveDTO;
import com.paddleocr.web.model.dto.OrgUpdateDTO;
import com.paddleocr.web.model.vo.OrgTreeVO;

import java.util.List;

/**
 * 机构服务（系统设置域）：树查询、增删改、删除保护校验
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public interface OrgService {

    /**
     * 查询机构树（一次查全表内存组树，按 sort、id 排序）
     *
     * @return 机构树节点集合，根节点 parentId=0
     */
    List<OrgTreeVO> listTree();

    /**
     * 新增机构（校验父机构存在且启用、机构编码唯一）
     *
     * @param dto 机构新增请求
     * @return 新机构 ID（字符串形式）
     */
    String saveOrg(OrgSaveDTO dto);

    /**
     * 修改机构名称/排序/状态（orgCode、parentId 本期禁止修改）
     *
     * @param id 机构 ID
     * @param dto 机构修改请求
     */
    void updateOrg(Long id, OrgUpdateDTO dto);

    /**
     * 删除机构（三重校验：存在 → 无子机构 → 无在职员工）
     *
     * @param id 机构 ID
     */
    void removeOrg(Long id);
}
