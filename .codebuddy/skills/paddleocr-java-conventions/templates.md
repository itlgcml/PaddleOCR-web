# paddleocr-java-conventions 完整模板

> 生成代码时按本模板复制，不要凭记忆重写。所有模块根包一律 `com.paddleocr.web`。

目录结构：

```
backend/
├── pom.xml                 # 父 POM：paddle-ocr-backend，版本收口 + 公共依赖
├── ocr-common/             # 统一响应体、错误码、业务异常（零 Spring 依赖）
├── ocr-client/             # 外部服务 HTTP 集成（HttpClient5，双客户端）
├── ocr-dal/                # DO、Mapper、MyBatis-Plus 配置、审计填充
├── ocr-service/            # 业务编排、事务边界、出参 VO
├── ocr-web/                # 启动类、Controller、全局异常、配置、唯一可执行 jar
└── sql/                    # 建表 DDL
```

## 1. pom.xml（父 POM + 5 个子模块）

`backend/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.paddleocr</groupId>
    <artifactId>paddle-ocr-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>paddle-ocr-backend</name>

    <modules>
        <module>ocr-common</module>
        <module>ocr-client</module>
        <module>ocr-dal</module>
        <module>ocr-service</module>
        <module>ocr-web</module>
    </modules>

    <properties>
        <java.version>1.8</java.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <druid.version>1.2.28</druid.version>
        <httpclient5.version>5.6.4</httpclient5.version>
        <!-- 覆盖 Boot 2.7.18 BOM 管理的 httpcore5 5.1.5（无 classic.methods 包），与 httpclient5 5.6.4 匹配 -->
        <httpcore5.version>5.4.3</httpcore5.version>
        <httpcore5-h2.version>5.4.3</httpcore5-h2.version>
        <springdoc.version>1.8.0</springdoc.version>
        <!-- springdoc 1.8.0 配套 swagger-core 线，仅供 @Schema 注解编译使用 -->
        <swagger-annotations.version>2.2.21</swagger-annotations.version>
        <hutool.version>5.8.47</hutool.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- 内部模块 -->
            <dependency>
                <groupId>com.paddleocr</groupId>
                <artifactId>ocr-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.paddleocr</groupId>
                <artifactId>ocr-client</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.paddleocr</groupId>
                <artifactId>ocr-dal</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.paddleocr</groupId>
                <artifactId>ocr-service</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- 第三方版本统一收口（Boot 2.x 专用 starter，禁止 boot3 版） -->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-boot-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-jsqlparser</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid-spring-boot-starter</artifactId>
                <version>${druid.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.httpcomponents.client5</groupId>
                <artifactId>httpclient5</artifactId>
                <version>${httpclient5.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springdoc</groupId>
                <artifactId>springdoc-openapi-ui</artifactId>
                <version>${springdoc.version}</version>
            </dependency>
            <dependency>
                <groupId>io.swagger.core.v3</groupId>
                <artifactId>swagger-annotations</artifactId>
                <version>${swagger-annotations.version}</version>
            </dependency>
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>${hutool.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- 所有子模块公共依赖 -->
    <dependencies>
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <configuration>
                        <excludes>
                            <exclude>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </exclude>
                        </excludes>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

`ocr-common/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paddleocr</groupId>
        <artifactId>paddle-ocr-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>ocr-common</artifactId>
    <name>ocr-common</name>
    <description>通用契约：统一响应体、错误码、业务异常（零 Spring 依赖）</description>

    <!-- 无额外依赖：swagger-annotations / lombok / test 由父 POM 公共段继承 -->
</project>
```

`ocr-client/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paddleocr</groupId>
        <artifactId>paddle-ocr-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>ocr-client</artifactId>
    <name>ocr-client</name>
    <description>外部集成：调用已有 OCR 识别服务（HttpClient5，超时/重试/异常转换）</description>

    <dependencies>
        <dependency>
            <groupId>com.paddleocr</groupId>
            <artifactId>ocr-common</artifactId>
        </dependency>
        <!-- @Component / @ConfigurationProperties 支持（非 web） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.httpcomponents.client5</groupId>
            <artifactId>httpclient5</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
    </dependencies>
</project>
```

`ocr-dal/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paddleocr</groupId>
        <artifactId>paddle-ocr-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>ocr-dal</artifactId>
    <name>ocr-dal</name>
    <description>数据访问：实体、Mapper、MyBatis-Plus 配置、审计填充</description>

    <dependencies>
        <!-- 数据层：MyBatis-Plus + Druid + MySQL（Boot 2.x 专用 starter，禁止 boot3 版） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- OcrRecordDO 的 @JsonSerialize(Long id 转字符串) -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

`ocr-service/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paddleocr</groupId>
        <artifactId>paddle-ocr-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>ocr-service</artifactId>
    <name>ocr-service</name>
    <description>业务层：识别/历史记录业务编排、事务边界、出参 VO</description>

    <dependencies>
        <dependency>
            <groupId>com.paddleocr</groupId>
            <artifactId>ocr-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.paddleocr</groupId>
            <artifactId>ocr-client</artifactId>
        </dependency>
        <dependency>
            <groupId>com.paddleocr</groupId>
            <artifactId>ocr-dal</artifactId>
        </dependency>
        <!-- @Service / @Transactional（spring-tx 由 mybatis-plus starter 传递） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <!-- OcrService 接口签名使用 MultipartFile（后续可选优化去掉） -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
    </dependencies>
</project>
```

`ocr-web/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paddleocr</groupId>
        <artifactId>paddle-ocr-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>ocr-web</artifactId>
    <name>ocr-web</name>
    <description>启动/门面模块：Controller、全局异常、配置、唯一可执行 jar</description>

    <dependencies>
        <dependency>
            <groupId>com.paddleocr</groupId>
            <artifactId>ocr-service</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <!-- API 文档（springdoc 1.x 线适配 Boot 2.x） -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-ui</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## 2. application.yml（ocr-web 模块，公共 + 多环境）

`ocr-web/src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  profiles:
    active: dev            # 启动时可用 --spring.profiles.active=prod 覆盖
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss   # 对 java.util.Date 生效；LocalDateTime 见 JacksonConfig
    time-zone: GMT+8
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/paddle_ocr?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}          # 禁止明文，必须环境变量
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      validation-query: SELECT 1
      test-while-idle: true
      stat-view-servlet:
        enabled: true
        login-username: ${DRUID_USER:admin}
        login-password: ${DRUID_PASSWORD}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: assign_id
      logic-delete-field: deleted
      logic-not-delete-value: 0
      logic-delete-value: 1
  mapper-locations: classpath*:mapper/*.xml

ocr:
  service:
    base-url: ${OCR_BASE_URL:http://ocr-service:8000}
    timeout-ms: 600000      # OCR 推理响应超时（本地推理慢，默认 10 分钟）

api:
  timeout-ms: 30000         # 通用 API 响应超时
```

`ocr-web/src/main/resources/application-dev.yml`（开发环境差异项）：

```yaml
spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: true
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 开发期打印 SQL
```

`ocr-web/src/main/resources/application-prod.yml`：

```yaml
spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: false        # 生产关闭监控页（或加 IP 白名单）
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 3. ocr-common（响应体 + 异常）

```java
package com.paddleocr.web.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "统一响应体")
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "0=成功，非 0=失败")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    @Schema(description = "服务端时间戳(ms)")
    private long timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<T>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ResultCode rc) {
        ApiResponse<T> r = new ApiResponse<T>();
        r.code = rc.getCode();
        r.message = rc.getMessage();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<T>();
        r.code = code;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }
}
```

```java
package com.paddleocr.web.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 错误码：0 成功；4xxxx 客户端；5xxxx 系统；503xx OCR 服务段 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(40000, "参数错误"),
    NOT_FOUND(40400, "资源不存在"),
    FILE_TYPE_NOT_ALLOWED(40010, "不支持的文件类型"),
    FILE_TOO_LARGE(40011, "文件大小超出限制"),
    SYSTEM_ERROR(50000, "系统内部错误"),
    DB_ERROR(50001, "数据库操作失败"),
    OCR_SERVICE_UNAVAILABLE(50301, "OCR 识别服务不可用"),
    OCR_SERVICE_TIMEOUT(50302, "OCR 识别超时"),
    OCR_RECOGNIZE_FAILED(50303, "OCR 识别失败");

    private final int code;
    private final String message;
}
```

```java
package com.paddleocr.web.common;

import lombok.Getter;

/** 业务异常：service/client 层抛出，由 GlobalExceptionHandler 统一转换 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public BusinessException(ResultCode rc, String detail) {
        super(detail);
        this.code = rc.getCode();
    }
}
```

## 4. ocr-dal（MyBatis-Plus 配置 + 实体示例）

配置类在 `com.paddleocr.web.mapper.config` 包：

```java
package com.paddleocr.web.mapper.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

```java
package com.paddleocr.web.mapper.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 审计字段自动填充：禁止在业务代码中手动 set createTime/updateTime */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

实体示例（`com.paddleocr.web.entity`，表 `t_ocr_record`；Mapper 接口放 `com.paddleocr.web.mapper`）：

```java
package com.paddleocr.web.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_ocr_record")
public class OcrRecordDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 雪花 ID，序列化为字符串防前端精度丢失 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String fileName;

    private String imagePath;

    private String resultText;

    /** 识别结果原始 JSON，仅存库，列表页不回传 */
    private String resultJson;

    private Long costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```

建表 DDL 约定：表名 `t_` 前缀、字段下划线命名、`id BIGINT` 主键、`create_time`/`update_time DATETIME DEFAULT CURRENT_TIMESTAMP`、`deleted TINYINT DEFAULT 0`；脚本存 `backend/sql/`。

## 5. ocr-client（HTTP 集成）

```java
package com.paddleocr.web.client.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP 客户端统一配置：单例连接池，连接超时统一 5s，keep-alive 30s
 * <ul>
 *   <li>ocrHttpClient：OCR 推理专用，响应超时由 ocr.service.timeout-ms（默认 10 分钟）决定</li>
 *   <li>apiHttpClient：通用 API 调用，响应超时由 api.timeout-ms（默认 30 秒）决定</li>
 * </ul>
 * 两客户端独立连接池，长耗时的 OCR 请求不挤占通用 API 连接
 */
@Configuration
public class HttpClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient ocrHttpClient() {
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(cm)
                .setKeepAliveStrategy((response, context) -> Timeout.ofSeconds(30))
                .build();
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient apiHttpClient(ApiProperties props) {
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(props.getTimeoutMs()))
                        .build())
                .setKeepAliveStrategy((response, context) -> Timeout.ofSeconds(30))
                .build();
    }
}
```

> 注：`ocrHttpClient` 的响应超时是请求级的，在 `OcrClient` 中随请求设置：`RequestConfig.custom().setResponseTimeout(Timeout.ofMilliseconds(props.getTimeoutMs())).build()`。

```java
package com.paddleocr.web.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** ocr.service 前缀类型安全绑定，禁止 @Value 散落 */
@Data
@Component
@ConfigurationProperties(prefix = "ocr.service")
public class OcrServiceProperties {

    private String baseUrl;

    private long timeoutMs = 600000;
}
```

```java
package com.paddleocr.web.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** api 前缀类型安全绑定，禁止 @Value 散落 */
@Data
@Component
@ConfigurationProperties(prefix = "api")
public class ApiProperties {

    /** 通用 API 响应超时（毫秒） */
    private long timeoutMs = 30000;
}
```

```java
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
```

```java
package com.paddleocr.web.client;

import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 通用 API 客户端：GET / POST JSON，连接 5s，响应超时由 api.timeout-ms（默认 30 秒）决定
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiClient {

    private final CloseableHttpClient apiHttpClient;

    /**
     * GET 请求，返回响应体字符串（UTF-8）
     */
    public String get(String url) {
        return execute(url, new HttpGet(url));
    }

    /**
     * POST JSON 请求，返回响应体字符串（UTF-8）
     */
    public String postJson(String url, String json) {
        HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        return execute(url, post);
    }

    private String execute(String url, HttpUriRequestBase request) {
        HttpClientResponseHandler<String> handler = (ClassicHttpResponse response) -> {
            int code = response.getCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (code != 200) {
                log.warn("API 调用失败, status={}, url={}", code, url);
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "API 返回 " + code);
            }
            return body;
        };
        try {
            return apiHttpClient.execute(request, handler);
        } catch (IOException e) {
            log.error("API 调用异常, url={}", url, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "API 调用异常");
        }
    }
}
```

`OcrClient`（OCR 推理专用，注入 `ocrHttpClient` + `OcrServiceProperties`，路径用 `OcrApi.RECOGNIZE`，失败抛 `ResultCode.OCR_SERVICE_*`）按上述规则实现；外部服务出参 VO 放 `com.paddleocr.web.client.model`。

## 6. ocr-web / ocr-service（启动 + 全局配置）

启动类（ocr-web，`@MapperScan` 扫 ocr-dal 中定义的 mapper 接口）：

```java
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
```

```java
package com.paddleocr.web.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/** LocalDateTime 全局 yyyy-MM-dd HH:mm:ss（spring.jackson.date-format 对其无效） */
@Configuration
public class JacksonConfig {

    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .deserializers(new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
    }
}
```

```java
package com.paddleocr.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 开发期前后端分离跨域；生产由 Nginx 同域转发，可关闭 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
```

全局异常（ocr-web 的 `controller.advice` 包）：

```java
package com.paddleocr.web.controller.advice;

import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.fail(ResultCode.PARAM_ERROR.getCode(),
                "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return ApiResponse.fail(ResultCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ApiResponse<Void> handleNotFound(NoHandlerFoundException e) {
        return ApiResponse.fail(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail(ResultCode.SYSTEM_ERROR);
    }
}
```

通用分页 VO（ocr-service 的 `model` 包）：

```java
package com.paddleocr.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果
 */
@Data
@Schema(description = "分页结果")
public class PageResultVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页数据")
    private List<T> records;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "当前页码")
    private long pageNum;

    @Schema(description = "每页条数")
    private long pageSize;
}
```
