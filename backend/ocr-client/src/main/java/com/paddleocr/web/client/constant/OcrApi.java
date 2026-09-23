package com.paddleocr.web.client.constant;

/**
 * OCR 服务接口路径常量：与上游服务契约绑定，变更需同步修改代码
 */
public enum OcrApi {

    /** 版面解析接口（JSON body：file 为 Base64 字符串，图片单页 / PDF 按页返回结构化结果） */
    LAYOUT_PARSING("/layout-parsing");

    private final String path;

    OcrApi(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
