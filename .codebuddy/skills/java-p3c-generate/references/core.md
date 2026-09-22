# 核心生成铁律（每次生成必读）

## 0. 项目铁律速览（完整版见 `../../paddleocr-java-conventions/SKILL.md`）

| 项 | 约束 |
|---|---|
| JDK / Boot | JDK 8、Spring Boot 2.7.18、`javax.*`（禁 `jakarta.*`） |
| 模块方向 | `ocr-web → ocr-service → {ocr-client → ocr-common, ocr-dal}`，禁止倒置与循环依赖 |
| 根包 | 全部 `com.paddleocr.web.*`，禁止额外 `@ComponentScan` |
| 版本收口 | 版本号只在父 POM 的 properties + dependencyManagement，子模块依赖不带 version |
| 模块职责 | common=零 Spring 依赖；client=外部 HTTP；dal=DO/Mapper/XML；service=业务编排；web=Controller/配置 |
| 响应体 | 一律 `ApiResponse<T>`；错误码 `ResultCode`；业务异常 `BusinessException` |
| 持久层 | MyBatis-Plus 3.5.17（`mybatis-plus-boot-starter` + `mybatis-plus-jsqlparser`），仅 ocr-dal |
| 连接池 | Druid 1.2.28（`druid-spring-boot-starter`），仅 ocr-dal |
| API 文档 | springdoc-openapi-ui 1.8.0，仅 ocr-web |
| yml | 只放 `ocr-web/src/main/resources/`；敏感值用 `${ENV_VAR}` 占位 |

### JDK 8 语法红线（P3C 示例中的新语法必须降级）

| 禁用（JDK 9+） | 替代 |
|---|---|
| `var` | 显式类型 |
| `List.of()` / `Map.of()` / `Set.of()` | `new ArrayList<>()` + `Collections.unmodifiableXxx` 或 Hutool `CollUtil` |
| `stream.toList()` | `.collect(Collectors.toList())` |
| `Optional.isEmpty()` / `ifPresentOrElse` | `!opt.isPresent()` |
| `String.isBlank()` / `strip()` / `repeat()` | `StrUtil.isBlank()`（Hutool） |
| Text Blocks | 普通字符串拼接 |
| record 类 | class + Lombok `@Data` |
| switch 表达式 / 接口私有方法 | 传统 switch / 默认方法或工具类 |

JDK 8 可用：Lambda、Stream、`Optional`、`LocalDateTime`、`CompletableFuture`、try-with-resources。

---

## 1. 命名（NM-01 ~ NM-16）

- 类名 `UpperCamelCase`（DO/BO/DTO/VO/AO 后缀例外）；方法/参数/成员/局部变量 `lowerCamelCase`
- 常量全大写 + 下划线且语义完整：`MAX_UPLOAD_SIZE_BYTES`，不要 `MAX_SIZE`
- 包名全小写、单数；本项目根包 `com.paddleocr.web.*`
- 抽象类 `Abstract`/`Base` 开头；异常类 `Exception` 结尾；测试类 `XxxTest`
- POJO 布尔字段**不加 `is` 前缀**（NM-08 / ORM-02）
- 杜绝不规范缩写；用了设计模式就体现在名字里
- 接口方法不加修饰符并写 Javadoc；接口内尽量不定义变量
- Service/DAO 必须接口 + `Impl` 后缀实现（强制项）
- 分层后缀：`xxxDO`（表名）/ `xxxDTO`（业务）/ `xxxVO`（展示）/ `xxxQuery`（超过 2 个查询参数），禁止 `xxxPOJO`

## 2. 常量（CST-01 ~ CST-05）

- **禁止魔法值**直接出现在代码中；`-1/0/1/2` 等惯用字面量除外
- `long`/`Long` 赋值用大写 `L`
- 按功能归类常量类，不要一个大而全的常量类
- 值域固定的用 `enum` 定义；枚举成员全大写且必须写注释

## 3. 代码格式（FMT-01 ~ FMT-11）

- 4 空格缩进，禁 tab；单行不超过 120 字符
- 大括号：左括号前不换行、后换行；右括号前换行；`else` 前不换行；空块直接 `{}`
- `if/for/while/switch/do` 与括号间加空格；二目/三目运算符两侧加空格
- 注释的双斜线后且仅一个空格；多参数逗号后加空格
- UTF-8 编码 + Unix 换行；不同语义代码块之间插一个空行

## 4. OOP（OOP-01 ~ OOP-20）

- 覆写方法必须 `@Override`
- 用常量或有值对象调 `equals`，或直接 `Objects.equals(a, b)`
- 包装类之间**一律 `equals`**，禁止 `==`
- POJO 属性**必须用包装类型**；接口入参出参同样用包装类型；局部变量可用基本类型
- POJO **不设属性默认值**；POJO 必须有 `toString`（Lombok `@Data` 已覆盖）
- 序列化类新增属性不改 `serialVersionUID`
- 构造方法禁止业务逻辑，初始化逻辑放 `init()`
- 类内顺序：public/protected → private → getter/setter；循环内字符串拼接用 `StringBuilder`
- 访问控制从严：工具类私有构造、仅本类使用的成员与方法一律 private

## 5. 集合（COL-01 ~ COL-13）

- 重写 `equals` 必须重写 `hashCode`；Set 元素、Map 的 key 必须重写两者
- `subList` 结果**不可强转** `ArrayList`，且不要同时修改原集合
- 集合转数组用 `toArray(new T[list.size()])`
- `Arrays.asList()` 的结果**不可** add/remove/clear
- **禁止在 foreach 中 remove/add**，改用 `Iterator.remove()` 或 `removeIf`
- `Comparator` 必须满足自反性、传递性、对称性三条件
- 集合初始化指定容量（HashMap 按 `元素数 / 0.75 + 1`）
- 遍历 Map 用 `entrySet`；`ConcurrentHashMap` 的 K/V **不允许 null**

## 6. 并发（CON-01 ~ CON-15）

- 线程池**必须** `ThreadPoolExecutor` 显式参数 + 具名 ThreadFactory，**禁 `Executors`**
- 创建线程必须指定有意义的名称
- 禁 `static SimpleDateFormat`；用 `LocalDateTime` + `DateTimeFormatter`（线程安全）或 Hutool 工具类
- 多资源加锁保持**一致顺序**；锁块尽量小，**锁内禁止 RPC/HTTP 调用**
- 并发修改同一记录用乐观锁 `version`
- 定时任务用 `ScheduledExecutorService`，不用 `Timer`
- 计数用 `AtomicInteger` / `LongAdder`，不用 `count++`

## 7. 控制语句（CTRL-01 ~ CTRL-10）

- `switch` 必须有 `default` 并放最后；每个 `case` 用 `break/return` 终止或注释说明继续执行的去向
- `if/else/for/while/do` **必须加大括号**，即使只有一行
- 高并发场景不用"等于"作为中断/退出条件，改用大于或小于区间判断
- `if-else` 不超过 3 层，超过用卫语句 / 策略模式 / 状态模式
- 条件判断中不放复杂表达式，先赋给有意义的布尔变量
- 循环体内不做定义对象、获取连接、try-catch 等可外移的操作
- 避免取反逻辑运算符

## 8. 注释（CMT-01 ~ CMT-11）

- 类、类属性、类方法用 Javadoc `/** */`，不用 `//`；所有类必须写创建者与创建日期
- 抽象方法（含接口方法）必须写 Javadoc，说明功能、参数、返回值、异常
- 枚举每个字段必须注释用途
- 方法内单行注释在被注释语句**上方另起一行**；多行用 `/* */`
- 注释用中文把问题说清楚，专有名词保留英文；代码改了注释同步改
- `TODO`/`FIXME` 必须标注标记人与时间

## 9. 异常（EX-01 ~ EX-13）

- 可用预检查规避的 `RuntimeException`（NPE、越界等）**不要 catch**，先判空/判边界
- 异常不做流程控制、不做条件控制
- catch 时区分稳定/非稳定代码，按异常类型分类处理；**禁止捕获后什么都不做**
- 捕获是为了处理；处理不了就抛给调用者，最外层必须转成用户可理解的信息
- 资源/流必须在 `finally` 关闭或用 try-with-resources（JDK 7+）
- **`finally` 中禁止 `return`**
- 捕获类型必须是抛出类型的本身或父类
- 禁止抛 `new RuntimeException()` / `Exception` / `Throwable`，统一用 `BusinessException(ResultCode)`
- 远程调用返回对象一律判空；级联调用 `a.getB().getC()` 必做 NPE 防护
- 相同校验逻辑抽取私有方法，遵守 DRY

## 10. 日志（LOG-01 ~ LOG-08 / OTH）

- 只用 SLF4J（本项目用 Lombok `@Slf4j`），禁止 Log4j/Logback 直用、禁止 `System.out.println`
- 一律占位符 `log.info("ocr result id: {}", id)`，禁止字符串拼接
- 异常必须打印堆栈：`log.error("recognize failed, id={}", id, e)`
- 参数错误用 warn，系统异常才用 error；避免重复打印日志
- **禁止打印敏感信息**：图片字节、base64、完整 OCR 大文本、密码、token
- 正则要预编译为 `static final Pattern`，不要在方法体内 `Pattern.compile`
- 取毫秒用 `System.currentTimeMillis()`（统计耗时用 `System.nanoTime()`）
- 及时清理无用代码与被注释掉的代码段
