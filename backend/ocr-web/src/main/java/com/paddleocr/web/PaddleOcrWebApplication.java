package com.paddleocr.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PaddleOCR-web 启动类
 */
@SpringBootApplication
@MapperScan("com.paddleocr.web.mapper")
public class PaddleOcrWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaddleOcrWebApplication.class, args);
    }
}
