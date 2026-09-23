# 生成 Service 层规则（ocr-service：业务编排 / DTO / VO / 事务 / 并发 / 单测）

加载时机：生成 `XxxService` / `XxxServiceImpl` / 业务 DTO / 出参 VO / 事务 / 线程池 / 异步。

## 1. 模块与包

- 模块 `ocr-service`；包 `com.paddleocr.web.service`（`.impl`）、`com.paddleocr.web.model`
- 必须先写接口 `XxxService`，再写实现 `XxxServiceImpl`（NM-14 强制）
- 禁止出现 Controller 相关类，禁止写 HTTP 调用细节（HTTP 归 `ocr-client`）

## 2. 方法命名（NM-16）

| 语义 | 前缀 |
|---|---|
| 获取单个对象 | `getXxx` |
| 获取多个对象 | `listXxx` |
| 统计值 | `countXxx` |
| 新增 | `saveXxx` / `insertXxx` |
| 删除 | `removeXxx` / `deleteXxx` |
| 修改 | `updateXxx` |

超过 2 个查询参数封装为 `XxxQuery`，**禁止用 `Map` 传递**。

## 3. 事务与调用边界

- `@Transactional(rollbackFor = Exception.class)` 只加在 `XxxServiceImpl` 的方法上
- **事务方法内禁止 HTTP 调用**（OCR 推理可达 10 分钟，会长期占连接）：先调 OCR，成功后用短事务落库
- 事务内 catch 异常若要回滚，注意手动回滚（EX-05）
- 事务不要滥用（ORM-09）

## 4. 异常与日志

- 业务失败抛 `BusinessException(ResultCode.Xxx)`，禁止抛 `RuntimeException` / `Exception` / `Throwable`（EX-11）
- 禁止吞异常（EX-04）；异常信息带现场参数，`log.error("msg, id={}", id, e)` 保留堆栈
- `@Slf4j` + 占位符；禁 `System.out.println`、禁字符串拼接日志、禁打印图片字节与 OCR 大文本

## 5. 参数与返回值

- Service 入参校验（业务规则），Controller 侧做格式校验；远程调用返回与级联调用必做判空（EX-10）
- 出参用 `XxxVO` / `PageResultVO`，属性包装类型、不设默认值
- 分页返回固定 `{ records, total, pageNum, pageSize }`，与前端契约一致
- 返回可能为 null 的方法必须在 Javadoc 中注明（EX-09）

## 6. 并发与异步

- 线程池：`ThreadPoolExecutor` 显式 7 参数 + 具名 ThreadFactory；**禁 `Executors`**（CON-03/04）
- `@Async` 必须指定自定义 executor
- 多资源加锁顺序一致，锁块内不做远程调用（CON-06/07）
- 并发改同一记录用乐观锁 `version`（CON-08）
- 计数用 `AtomicInteger` / `LongAdder`

## 7. 单元测试（UT-01 ~ UT-16）

**本技能不生成任何测试代码**：`src/test/**` 下的单元测试一律不写，由专门的测试 skill 负责。仅在用户明确要求查阅测试规约时，参考 `java-p3c-review` 的 UT-01 ~ UT-16 条文。

## 8. 自检

- 是否接口 + Impl 成对、方法前缀是否符合规约
- 事务注解是否只在 Impl、事务内是否有 HTTP 调用
- 是否有吞异常、裸抛 RuntimeException、日志拼接与打印敏感数据
- 是否使用 `Executors`、`static SimpleDateFormat`
