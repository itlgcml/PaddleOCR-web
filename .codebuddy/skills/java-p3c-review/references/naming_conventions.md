# P3C 规约命名与编号规范

## 规约条目编号格式

格式：`<维度缩写>-<序号>`，如 `OOP-04`（OOP规范-04）

## 维度缩写对照表

| 全称 | 缩写 | 参考文件 | 关键词映射 |
|------|------|----------|-----------|
| Naming | NM | `knowledge/programming/naming.md` | 命名、name、class名 |
| Constants | CST | `knowledge/programming/constants.md` | 常量、constant、魔法值 |
| Format | FMT | `knowledge/programming/format.md` | 格式、format、缩进 |
| OOP | OOP | `knowledge/programming/oop.md` | 面向对象、OOP、equals |
| Collections | COL | `knowledge/programming/collections.md` | 集合、collection、List、Map |
| Concurrency | CON | `knowledge/programming/concurrency.md` | 并发、线程、thread、ThreadPool |
| Control | CTL | `knowledge/programming/control.md` | 控制、if、for、switch |
| Comments | CMT | `knowledge/programming/comments.md` | 注释、javadoc、文档 |
| Exception | EXP | `knowledge/exception_log.md` | 异常、exception、try-catch |
| Log | LOG | `knowledge/exception_log.md` | 日志、log、logger |
| UnitTest | UT | `knowledge/unit_test.md` | 测试、单元测试、junit |
| Security | SEC | `knowledge/security.md` | 安全、SQL注入、加盐 |
| SQL | SQL | `knowledge/mysql.md` | MySQL、SQL、索引、数据库 |
| Engineering | ENG | `knowledge/engineering.md` | 工程、分层、Controller |
| QuickIndex | QI | `knowledge/quick_index.md` | 索引、quick、目录 |

## 编号解析规则

### 有效编号格式
- `NM-01` ~ `NM-99`：命名风格规约
- `OOP-01` ~ `OOP-99`：OOP规范
- `COL-01` ~ `COL-99`：集合处理规约
- `CON-01` ~ `CON-99`：并发处理规约
- `EXP-01` ~ `EXP-99`：异常处理规约
- `SQL-01` ~ `SQL-99`：MySQL规约
- `ENG-01` ~ `ENG-99`：工程结构规约

### 无效编号示例
- `XX-01`：未知维度前缀
- `NM-1`：序号必须为两位数字
- `OOP`：缺少序号
- `001`：缺少维度前缀
