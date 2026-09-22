# P3C 代码审查流程与检查清单

## 分阶段审查流程

P3C 代码审查支持**分阶段执行**，便于大型代码库的渐进式审查。

### 阶段划分

| 阶段 | 审查内容 | 主要规约维度 |
|------|----------|-------------|
| 阶段 1 | 语法与格式审查 | 命名风格、常量定义、代码格式 |
| 阶段 2 | OOP 与集合审查 | OOP 规范、集合处理 |
| 阶段 3 | 并发与异常审查 | 并发处理、异常日志 |
| 阶段 4 | 工程结构审查 | 工程结构、MySQL 规约、安全规约 |

### 使用命令

- `/p3c 审查 <文件>` → 完整审查（所有阶段）
- `/p3c 审查 <文件> 阶段1` → 仅审查阶段 1
- `/p3c 继续` → 继续下一阶段审查

---

## 审查流程（完整版）

### 1. 接收代码
获取待审查的 Java 代码片段或文件列表，确认审查范围。

### 2. 识别技术维度
分析代码涉及的技术领域，确定需要加载的 reference 文件：

| 代码特征 | 对应 reference |
|----------|---------------|
| 类/方法/变量命名、常量、代码格式 | `programming/naming.md`, `programming/constants.md`, `programming/format.md` |
| 集合操作（List/Map/Set/Array） | `programming/collections.md` |
| 线程/线程池/锁/volatile | `programming/concurrency.md` |
| try-catch/finally/异常抛出 | `exception_log.md` → 异常处理 |
| logger/slf4j/log4j | `exception_log.md` → 日志规约 |
| @Test/单元测试代码 | `unit_test.md` |
| SQL/MyBatis/JDBC | `mysql.md` |
| pom.xml/Maven/依赖 | `engineering.md` → 二方库 |
| DAO/Service/Controller 分层 | `engineering.md` → 应用分层 |

### 3. 加载对应 reference
使用 `read_file` 加载涉及的 reference 文件内容。

### 4. 逐项检查
按 **【强制】→【推荐】→【参考】** 的顺序，逐条对照规约检查代码。

### 5. 输出审查结果
使用 `templates/review_template.md` 格式输出审查报告。

### 6. 给出修改建议
每条问题附带头尾说明和正例代码，便于开发者直接修改。

---

## 问题严重度评估标准

### Critical（P0 - 必须立即修复）

违反会导致系统故障、安全漏洞或严重性能问题：

| 规约维度 | 严重问题示例 |
|----------|-------------|
| 并发处理 | 在并发场景下使用非线程安全的类（如 HashMap）进行共享数据操作 |
| 异常处理 | catch 块中吞掉异常导致错误被忽略；finally 块中有 return 语句 |
| 安全规约 | SQL 注入风险；敏感数据未脱敏展示 |
| 集合处理 | 在 foreach 循环中修改集合导致 ConcurrentModificationException |
| OOP 规范 | 使用 == 比较包装类导致值比较错误 |

### Major（P1 - 本次迭代修复）

违反可能导致潜在 Bug 或维护困难：

| 规约维度 | 主要问题示例 |
|----------|-------------|
| OOP 规范 | 未正确使用 @Override；equals 和 hashCode 不一致 |
| 异常日志 | 使用错误的日志级别；异常信息缺少堆栈 |
| 集合处理 | subList 强转为 ArrayList 导致类型转换异常 |
| MySQL | 未建立必要索引导致性能问题；使用 select * |
| 工程结构 | 分层不清晰；DAO 层直接调用 DAO |

### Minor（P2 - 适当改进）

风格问题，不影响功能但影响可读性和维护性：

| 规约维度 | 次要问题示例 |
|----------|-------------|
| 命名风格 | 命名不规范；使用拼音命名 |
| 代码格式 | 缩进不一致；缺少空格 |
| 注释规约 | 缺少 Javadoc；TODO 没有责任人 |
| 常量定义 | 魔法值直接出现；常量未归类 |

---

## 各阶段检查清单

### 编程规约检查清单

### 阶段 1：语法与格式审查（阶段1）

聚焦基础代码质量和可读性，对应规约编号：NM（命名）、CST（常量）、FMT（格式）

#### 命名风格（NM）
- [ ] NM-01: 命名无下划线/美元符号开头或结尾
- [ ] NM-02: 无拼音与英文混合命名
- [ ] NM-03: 类名使用 UpperCamelCase
- [ ] NM-04: 方法/变量使用 lowerCamelCase
- [ ] NM-05: 常量全大写下划线分隔
- [ ] NM-06: POJO 布尔属性不加 is 前缀
- [ ] NM-07: 包名全小写单数形式

#### 常量定义（CST）
- [ ] CST-01: 无魔法值直接出现在代码中
- [ ] CST-02: long/L 使用大写 L
- [ ] CST-03: 常量按功能归类

#### 代码格式（FMT）
- [ ] FMT-01: 大括号使用约定（左不换行、右换行）
- [ ] FMT-02: 括号前后空格正确
- [ ] FMT-03: 4 空格缩进
- [ ] FMT-04: 单行不超过 120 字符
- [ ] FMT-05: UTF-8 编码、Unix 换行

---

### 阶段 2：OOP 与集合审查（阶段2）

聚焦面向对象设计和集合使用规范，对应规约编号：OOP、COL

#### OOP 规范（OOP）
- [ ] OOP-01: 静态变量用类名访问
- [ ] OOP-02: 覆写方法加 @Override
- [ ] OOP-03: equals 用常量调用（避免 NPE）
- [ ] OOP-04: 包装类用 equals 比较
- [ ] OOP-05: POJO 属性用包装类型
- [ ] OOP-06: POJO 不写属性默认值
- [ ] OOP-07: POJO 必须写 toString

#### 集合处理（COL）
- [ ] COL-01: 重写 equals 必重写 hashCode
- [ ] COL-02: subList 不强转 ArrayList
- [ ] COL-03: 集合转数组用 toArray(T[])
- [ ] COL-04: Arrays.asList 不修改（增删元素）
- [ ] COL-05: foreach 不 remove/add
- [ ] COL-06: Comparator 满足三条件（自反/对称/传递）

---

### 阶段 3：并发与异常审查（阶段3）

聚焦多线程和异常处理的高风险区域，对应规约编号：CON、EX、LOG

#### 并发处理（CON）- Critical 重点关注
- [ ] CON-01: 线程池必须通过 ThreadPoolExecutor 创建
- [ ] CON-02: 不允许使用 Executors 便捷方法创建线程池
- [ ] CON-03: SimpleDateFormat 不允许使用 static（线程不安全）
- [ ] CON-04: 锁粒度必须最小化
- [ ] CON-05: 多资源加锁顺序必须一致（避免死锁）

#### 异常处理（EX）
- [ ] EX-01: 不允许 catch RuntimeException 规避预检查异常
- [ ] EX-02: 异常不允许做流程控制
- [ ] EX-03: catch 必须区分异常类型处理
- [ ] EX-04: finally 必须关闭资源
- [ ] EX-05: finally 不允许有 return 语句（严重）

#### 日志规约（LOG）
- [ ] LOG-01: 统一使用 SLF4J 而非 Log4j/Logback
- [ ] LOG-02: 日志文件保存 >= 15 天
- [ ] LOG-03: 用占位符 `{}` 而非字符串拼接
- [ ] LOG-04: 异常信息必须包含堆栈

---

### 阶段 4：工程结构审查（阶段4）

聚焦架构、数据库和安全规范，对应规约编号：ENG、SQL、SEC

#### 工程结构（ENG）
- [ ] ENG-01: DAO → Manager → Service → Web 分层清晰
- [ ] ENG-02: 领域模型命名符合规范（DO/DTO/VO）
- [ ] ENG-03: Query 超过 2 参数不用 Map
- [ ] ENG-04: GAV 符合规范
- [ ] ENG-05: 版本号遵循 Semantic Versioning
- [ ] ENG-06: 线上不允许使用 SNAPSHOT
- [ ] ENG-07: 接口返回值不使用枚举（可能导致反序列化问题）
- [ ] ENG-08: JVM Xms = Xmx
- [ ] ENG-09: 配置 HeapDumpOnOOM

#### MySQL 数据库（SQL）
- [ ] SQL-01: 布尔字段 is_xxx 命名
- [ ] SQL-02: 表名/字段名小写
- [ ] SQL-03: 表名单数
- [ ] SQL-04: 禁用保留字
- [ ] SQL-05: 小数用 decimal 而非 float/double
- [ ] SQL-06: 表必备 id/gmt_create/gmt_modified 三字段
- [ ] SQL-07: 唯一特性字段建唯一索引
- [ ] SQL-08: 不超过三表 join
- [ ] SQL-09: varchar 字段索引必须指定长度
- [ ] SQL-10: 禁止左模糊/全模糊查询
- [ ] SQL-11: 用 count(*) 而非 count(列名)
- [ ] SQL-12: 禁止使用外键与级联
- [ ] SQL-13: 禁止使用存储过程
- [ ] SQL-14: 查询禁止使用 `*`
- [ ] SQL-15: 用 `#{}` 而非 `${}`（防注入）
- [ ] SQL-16: 更新必须同时修改 gmt_modified

#### 安全规约（SEC）- Critical 重点关注
- [ ] SEC-01: 用户页面必须有权限校验
- [ ] SEC-02: 敏感数据必须脱敏展示
- [ ] SEC-03: SQL 必须用参数绑定防注入
- [ ] SEC-04: 用户输入参数必须有效性校验
- [ ] SEC-05: 必须有 CSRF 安全过滤

---

## 快速参考索引

| 阶段 | 关键词 | 对应规约编号 |
|------|--------|-------------|
| 阶段 1 | 命名、常量、格式 | NM、CST、FMT |
| 阶段 2 | OOP、集合 | OOP、COL |
| 阶段 3 | 并发、异常、日志 | CON、EX、LOG |
| 阶段 4 | 工程、MySQL、安全 | ENG、SQL、SEC |

| 规约编号 | 完整名称 | 参考文件 |
|----------|----------|----------|
| NM | 命名风格 | `programming/naming.md` |
| CST | 常量定义 | `programming/constants.md` |
| FMT | 代码格式 | `programming/format.md` |
| OOP | OOP 规范 | `programming/oop.md` |
| COL | 集合处理 | `programming/collections.md` |
| CON | 并发处理 | `programming/concurrency.md` |
| CTL | 控制语句 | `programming/control.md` |
| CMT | 注释规约 | `programming/comments.md` |
| EX | 异常处理 | exception_log.md |
| LOG | 日志规约 | exception_log.md |
| UT | 单元测试 | unit_test.md |
| SEC | 安全规约 | security.md |
| SQL | MySQL 规约 | mysql.md |
| ENG | 工程结构 | engineering.md |
