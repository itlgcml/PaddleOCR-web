package com.paddleocr.web.client.model;

import java.io.Serializable;

/**
 * 上游版面解析接口（POST /layout-parsing）JSON 请求体
 *
 * <p>字段与上游契约一一对应（接口文档 §2）：file 为文件内容 Base64 字符串（无 data: 前缀），
 * fileType 显式声明（0=PDF，1=图像），visualize 关闭可视化结果图（前端按原始 PDF/图片预览），
 * useChartRecognition / useSealRecognition 固定开启图表识别与印章识别</p>
 *
 * @author paddleocr
 * @date 2026-09-23
 */
public class LayoutParsingRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件内容的 Base64 编码结果（上游亦支持 URL，本实现固定传 Base64） */
    private String file;

    /** 文件类型：0=PDF，1=图像 */
    private Integer fileType;

    /** 是否返回可视化结果图与中间图像，固定 false 关闭（前端按原始 PDF/图片预览，减少回包体积） */
    private Boolean visualize = Boolean.FALSE;

    /** 是否启用图表识别（对应上游 use_chart_recognition），固定 true：对 chart 版面区域做图表理解（图表转表格） */
    private Boolean useChartRecognition = Boolean.TRUE;

    /** 是否启用印章识别（对应上游 use_seal_recognition），固定 true：对 seal 版面区域做印章文字识别 */
    private Boolean useSealRecognition = Boolean.TRUE;

    public LayoutParsingRequest() {
    }

    public LayoutParsingRequest(String file, Integer fileType) {
        this.file = file;
        this.fileType = fileType;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public Integer getFileType() {
        return fileType;
    }

    public void setFileType(Integer fileType) {
        this.fileType = fileType;
    }

    public Boolean getVisualize() {
        return visualize;
    }

    public void setVisualize(Boolean visualize) {
        this.visualize = visualize;
    }

    public Boolean getUseChartRecognition() {
        return useChartRecognition;
    }

    public void setUseChartRecognition(Boolean useChartRecognition) {
        this.useChartRecognition = useChartRecognition;
    }

    public Boolean getUseSealRecognition() {
        return useSealRecognition;
    }

    public void setUseSealRecognition(Boolean useSealRecognition) {
        this.useSealRecognition = useSealRecognition;
    }
}
