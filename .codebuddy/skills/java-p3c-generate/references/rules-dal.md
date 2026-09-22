# 生成 DAL 层规则（ocr-dal：DO / Mapper / XML / DDL）

加载时机：生成实体、Mapper 接口、mapper XML、建表 DDL、索引、SQL 语句。

## 1. 模块与包

- 模块 `ocr-dal`；包 `com.paddleocr.web.mapper`（含 `config` 子包）、`com.paddleocr.web.entity`
- mapper XML 放 `ocr-dal/src/main/resources/mapper/`
- 建表 DDL 放 `backend/sql/`
- 禁止依赖 `ocr-client` / `ocr-service`；只被 `ocr-service` 使用

## 2. DO 实体生成要点

- 命名 `XxxDO`（表名对应，单数）；Lombok `@Data`；属性**包装类型**、**不设默认值**（OOP-08/09）
- 主键 `@TableId(type = IdType.ASSIGN_ID)` 雪花 Long；**Long 型 id 必须加 `@JsonSerialize(using = ToStringSerializer.class)`** 防前端精度丢失
- 审计字段 `@TableField(fill = FieldFill.INSERT)` / `INSERT_UPDATE`，由 `AuditMetaObjectHandler` 自动填充，禁止手动赋值
- 逻辑删除字段 `deleted` + `@TableLogic`
- 布尔属性不加 `is` 前缀；数据库字段用 `is_xxx`，在 resultMap 中做映射（ORM-02）
- 需要序列化时显式 `serialVersionUID`，后续新增属性不修改它（OOP-10）

## 3. Mapper 与查询

- 接口继承 `BaseMapper<XxxDO>`；简单 CRUD 不写 XML；多表 join / 统计才写 XML
- 条件构造优先 `LambdaQueryWrapper`，禁止 `apply` / `last` 拼接用户输入
- XML 参数一律 `#{}`，**禁止 `${}` 与字符串拼接**（ORM-04 / SEC-03）
- 禁止 `select *`，明确列出字段（ORM-01）；返回结果禁 `HashMap`/`Hashtable`（ORM-06），必须有 resultMap 或明确 resultType
- 不用 `resultClass` 当返回参数（ORM-03）
- 更新记录时必须同时更新更新时间字段（ORM-07）；不要"大而全"全字段更新（ORM-08）
- 分页：`MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.MYSQL)` 已在 `MybatisPlusConfig` 注册，参数用 `Page<T>`
- **count 为 0 时直接返回空分页**，不再执行分页查询（SQL-05）

## 4. 建表 DDL（TBL / IDX）

- 字段与表名全小写、数字不放开头、两下划线间不放纯数字；表名用单数；禁用保留字
- 是/否字段 `is_xxx` 命名，`unsigned tinyint`
- 小数用 `decimal`，禁 `float`/`double`；定长字符串用 `char`；`varchar` 不超过 5000，超长用 `text` 拆表
- 索引命名：`pk_字段` / `uk_字段` / `idx_字段`；组合索引区分度最高的放最左，等号条件列前置
- 业务唯一字段必须建唯一索引；`varchar` 索引必须指定长度；页面搜索严禁左模糊/全模糊
- 禁止超过三表 join；join 字段数据类型必须一致且有索引；注意隐式类型转换导致索引失效
- 禁止外键与级联、禁止存储过程
- 本项目必备字段：`id`(bigint 雪花)、`create_time`、`update_time`、`deleted`（P3C 的 `gmt_create`/`gmt_modified` 命名被项目规范覆盖，以项目为准）

## 5. SQL 语句

- 统计行数用 `count(*)`；`sum()` 注意 NPE，用 `IFNULL(SUM(x), 0)`（SQL-01/03）
- 判断 NULL 用 `ISNULL()`；`in` 集合控制在 1000 以内
- 数据订正（删/改）前先 `select` 确认
- 有 `order by` 时利用索引有序性；超多分页用延迟关联/子查询优化

## 6. 自检

- 是否有 `${}`、字符串拼接 SQL、`apply`/`last` 拼接用户输入
- 实体是否包装类型、是否误设默认值、Long id 是否加 `ToStringSerializer`
- XML 是否写了 `select *`、是否缺 resultMap
- DDL 是否有必备三字段、索引命名是否规范
