# 生成后自检清单（交付前必须逐项过）

## P0 阻断项（任一不通过 = 必须改完再交付）

| 编号 | 自检点 | 通过标准 |
|---|---|---|
| — | 模块归属 | DO/Mapper 在 ocr-dal；外部调用在 ocr-client；业务在 ocr-service；Controller/配置在 ocr-web |
| — | JDK 8 / Boot 2.7 | 无 `var`、`List.of`、`stream.toList()`、`isBlank`、Text Blocks、record；无 `jakarta.*` |
| — | 响应与异常 | 统一 `ApiResponse<T>`；异常统一 `BusinessException(ResultCode)` |
| COL-07 | foreach 内 remove/add | 无此类代码，用 `Iterator.remove()` / `removeIf` |
| CON-04 | 线程池创建 | 无 `Executors.*`，用 `ThreadPoolExecutor` 显式参数 |
| CON-05 | 日期格式化 | 无 `static SimpleDateFormat` |
| OOP-07 | 包装类比较 | Integer/Long 等一律 `equals`，无 `==` |
| OOP-08/09 | POJO 属性 | 全部包装类型，且无默认值 |
| EX-07 | finally return | 无 |
| EX-04 | 吞异常 | 无空 catch、无 catch 后不处理 |
| LOG-01 | 日志 API | 只用 SLF4J（`@Slf4j`），无 `System.out.println` |
| ORM-04 / SEC-03 | SQL 注入 | 无 `${}`、无字符串拼接 SQL、无 `apply`/`last` 拼接用户输入 |
| ENG-01 | 分层依赖 | Controller 不直连 Mapper；无模块倒置依赖 |
| CST-01 | 魔法值 | 无未经定义的字面量（-1/0/1/2 除外） |

## P1 重要项

| 编号 | 自检点 |
|---|---|
| NM-03/04/05 | 类名、方法名、常量命名规范 |
| NM-08 / ORM-02 | POJO 布尔字段无 `is` 前缀，DB 字段 `is_xxx` 并在 resultMap 映射 |
| NM-14 | Service/DAO 接口 + `Impl` 实现成对 |
| NM-16 | DO/DTO/VO/Query 后缀正确，无 `xxxPOJO` |
| OOP-02 | 覆写方法都有 `@Override` |
| OOP-06 | `equals` 用常量或 `Objects.equals` 调用 |
| COL-01 | 重写 `equals` 同时重写 `hashCode` |
| COL-05 | `Arrays.asList()` 结果未做增删 |
| COL-09 | 集合初始化指定容量 |
| CTL/CTRL-01/02 | `switch` 有 `default`；`if/for` 均带大括号 |
| CTRL-04 | `if-else` 不超过 3 层 |
| EX-01/02 | 无 catch RuntimeException 代替预检查；异常未用于流程控制 |
| EX-10 | 远程调用返回值、级联调用已判空 |
| LOG-04 | 日志用占位符，未字符串拼接 |
| LOG-06 | `log.error` 带堆栈 `e` |
| LOG-07 | 未打印图片字节、base64、token、密码 |
| OTH-01 | 正则预编译 |
| CMT-01/02/05 | 类/方法 Javadoc、枚举字段注释齐全，类有创建者与日期 |
| FMT-05/07 | 4 空格缩进、单行 ≤ 120 字符 |

## 领域附加项

- DAL：必备字段 `id/create_time/update_time/deleted`；Long id 加 `ToStringSerializer`；索引命名 `pk_/uk_/idx_`
- Service：`@Transactional(rollbackFor = Exception.class)` 且事务内无 HTTP 调用；方法前缀 get/list/count/save/remove/update
- Web：入参有 `@Validated` + 校验注解；无 try-catch 拼响应；上传白名单 + 魔数校验；yml 无明文密码
- Client：复用 `ocrHttpClient` / `apiHttpClient`；路径常量在 `OcrApi`；异常转换为 `BusinessException`
- Test：在 `src/test/java`，AIR 原则，无 `System.out`，外部依赖 Mock

## 复核方式

生成完成后，如需二次核验，调用审查技能或执行其静态检查脚本：

```bash
# 在 .codebuddy/skills 目录下
python java-p3c-review/scripts/p3c_checker.py <目标文件绝对路径>
```

再按 `java-p3c-review` 输出的报告修复问题，最终报告落在 `p3c_review_report.md`。

## 常见返工点（本项目高频）

| 错误写法 | 后果 | 正确写法 |
|---|---|---|
| 子模块依赖带 `<version>` | 版本漂移 | 父 POM `dependencyManagement` 收口 |
| 非 web 模块引 `spring-boot-starter-web` | 分层破坏 | 仅 ocr-web 可用 |
| `import jakarta.*` | ClassNotFoundException | `import javax.*` |
| 所有外呼共用一个 HttpClient | OCR 长耗时挤占通用连接 | `ocrHttpClient` / `apiHttpClient` 双 Bean |
| 事务内调 OCR HTTP | 连接占用/超时 | 调用与事务分离 |
| `new ObjectMapper()` | 配置不一致 | 注入全局 ObjectMapper |
| 前端 `id: number` | 雪花 ID 精度丢失 | `id: string` |
