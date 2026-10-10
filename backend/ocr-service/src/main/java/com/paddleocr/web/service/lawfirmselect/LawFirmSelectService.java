package com.paddleocr.web.service.lawfirmselect;

import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.LawFirmSelectSaveDTO;
import com.paddleocr.web.model.vo.LawFirmSelectVO;

/**
 * 律所选聘服务（业务审批域）：分页查询、增删改
 *
 * @author paddleocr
 * @date 2026-09-30
 */
public interface LawFirmSelectService {

    /**
     * 律所选聘分页查询
     *
     * @param pageNum 页码（≥1）
     * @param pageSize 每页条数（1~100）
     * @param title 按标题前缀匹配，可空
     * @return 分页结果
     */
    PageResultVO<LawFirmSelectVO> pageLawFirmSelects(int pageNum, int pageSize, String title);

    /**
     * 律所选聘详情查询
     *
     * @param id 律所选聘记录 ID
     * @return 律所选聘详情
     */
    LawFirmSelectVO getLawFirmSelectDetail(Long id);

    /**
     * 新增律所选聘记录
     *
     * @param dto 律所选聘新增请求
     * @return 新记录 ID（字符串形式）
     */
    String saveLawFirmSelect(LawFirmSelectSaveDTO dto);

    /**
     * 修改律所选聘记录（全字段可改）
     *
     * @param id 律所选聘记录 ID
     * @param dto 律所选聘修改请求
     */
    void updateLawFirmSelect(Long id, LawFirmSelectSaveDTO dto);

    /**
     * 删除律所选聘记录（逻辑删除）
     *
     * @param id 律所选聘记录 ID
     */
    void removeLawFirmSelect(Long id);
}
