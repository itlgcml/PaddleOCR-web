package com.paddleocr.web.client.constant;

/**
 * OCR 服务接口路径常量：与上游服务契约绑定，变更需同步修改代码
 */
public enum OcrApi {

    /** 图片识别接口 */
    RECOGNIZE("/ocr/recognize");

    private final String path;

    OcrApi(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
