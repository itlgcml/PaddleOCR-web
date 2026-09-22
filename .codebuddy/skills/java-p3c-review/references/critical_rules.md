# 关键规则速查（必须优先检查）

## 【强制】高优先级规则（P0 - Critical）

| 编号 | 规则摘要 | 严重度 | 检查方式 |
|------|----------|--------|----------|
| NM-01 | 类名 UpperCamelCase | P0 | 正则：class\s+([A-Z]\w*) |
| NM-02 | 方法/变量名 lowerCamelCase | P0 | 方法名以小写字母开头 |
| NM-03 | 类名 UpperCamelCase | P0 | 同 NM-01 |
| NM-04 | 枚举成员全大写下划线 | P0 | 正则：enum\s+\w+\s*\{[^}]*([A-Z_]+) |
| NM-05 | 抽象类名以 Abstract/Base 开头 | P0 | 类名前缀检查 |
| NM-06 | 接口名不加 I 前缀 | P0 | 排除 IXXX 格式的接口名 |
| COL-05 | foreach 循环中禁止 remove/add | P0 | 检测增强 for 循环中的集合修改 |
| CON-01 | 线程池必须用 ThreadPoolExecutor 创建 | P0 | 禁止 Executors 工厂方法 |
| CON-03 | SimpleDateFormat 不能用 static | P0 | 成员变量检查 |
| EXP-05 | finally 块中不能有 return | P0 | 语法树检测 |
| OOP-04 | 包装类用 equals 比较 | P0 | 禁止 == 比较 Integer/Long 等 |
| SQL-15 | SQL 用 #{} 而非 ${} | P0 | MyBatis 语句检查 |
| SEC-03 | SQL 参数绑定防注入 | P0 | PreparedStatement 使用检查 |
| ENG-01 | Controller 禁止直接操作数据库 | P0 | JDBC API 调用检测 |

## 【强制】中优先级规则（P1 - Major）

| 编号 | 规则摘要 | 严重度 | 检查方式 |
|------|----------|--------|----------|
| NM-08 | POJO 布尔字段不加 is 前缀 | P1 | 字段名检测 |
| CST-01 | 禁止魔法值（除 -1, 0, 1, 2） | P1 | 字面量检查 |
| OOP-05 | POJO 属性用包装类型 | P1 | 基本类型字段检测 |
| COL-01 | 重写 equals 必重写 hashCode | P1 | 方法对检测 |
| LOG-01 | 用 SLF4J 而非 Log4j/Log4j2 | P1 | 导入语句检查 |
| LOG-02 | 异常必须打印堆栈 | P1 | logger.error(e) 检测 |

## 【推荐】一般优先级规则（P2 - Minor）

| 编号 | 规则摘要 | 严重度 |
|------|----------|--------|
| FMT-01 | 每行不超过 120 字符 | P2 |
| FMT-03 | 运算符前后必须加空格 | P2 |
| CMT-01 | 类必须有 Javadoc | P2 |
| CMT-02 | 方法必须有 Javadoc（除 getter/setter） | P2 |

## 规则分类说明

### Phase 1 - 命名风格/常量定义/代码格式
- **NM-01~NM-06**：【强制】P0
- **CST-01**：【强制】P1
- **FMT-01~FMT-10**：【推荐】P2

### Phase 2 - OOP 规范/集合处理
- **OOP-04~OOP-05**：【强制】P0/P1
- **COL-01, COL-05**：【强制】P1/P0

### Phase 3 - 并发处理/控制语句/注释规约
- **CON-01, CON-03**：【强制】P0
- **CTL-01~CTL-12**：【强制】P0/P1

### Phase 4 - 异常处理/日志规约
- **EXP-05**：【强制】P0
- **LOG-01~LOG-02**：【强制】P1

### Phase 5 - MySQL 规范
- **SQL-15**：【强制】P0

### Phase 6 - 工程结构/单元测试/安全规约
- **ENG-01**：【强制】P0
- **SEC-03**：【强制】P0
