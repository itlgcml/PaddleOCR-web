# java-p3c-review Skill

基于阿里巴巴 Java 开发手册（P3C）的代码审查工具，支持分阶段审查和规约查询。

## 使用方法

### 完整代码审查
```
/java-p3c-review UserService.java
```

### 指定阶段审查
```
/java-p3c-review --phase=2 UserService.java
```

### 查询规约
```
/java-p3c-review --query=并发处理
```

### 继续审查
```
/java-p3c-review --continue
```

## 审查阶段

| 阶段 | 内容 |
|------|------|
| 阶段 1 | 命名风格、常量定义、代码格式 |
| 阶段 2 | OOP 规范、集合处理 |
| 阶段 3 | 并发处理、控制语句、注释规约 |
| 阶段 4 | 异常处理、日志规约 |
| 阶段 5 | MySQL 规范 |
| 阶段 6 | 工程结构、单元测试、安全规约 |

## 文件结构

```
skills/java-p3c-review/
├── SKILL.md              # 技能定义（核心指令文件）
├── README.md             # 本文件
├── knowledge/            # P3C 规约知识库
│   ├── programming/      # 编程规约细分
│   │   ├── naming.md
│   │   ├── constants.md
│   │   ├── format.md
│   │   ├── oop.md
│   │   ├── collections.md
│   │   ├── concurrency.md
│   │   ├── control.md
│   │   └── comments.md
│   ├── exception_log.md
│   ├── mysql.md
│   ├── engineering.md
│   ├── unit_test.md
│   ├── security.md
│   ├── glossary.md
│   ├── quick_index.md
│   └── review_guide.md
└── templates/
    └── review_template.md  # 审查报告模板
```

## 规约编号对照

- NM: 命名风格
- CST: 常量定义
- FMT: 代码格式
- OOP: OOP规范
- COL: 集合处理
- CON: 并发处理
- CTL: 控制语句
- CMT: 注释规约
- OTH: 其他
- EXP: 异常处理
- LOG: 日志规约
- SQL: MySQL规约
- ENG: 工程结构
- UT: 单元测试
- SEC: 安全规约

## P3C 版本

基于《Java开发手册(黄山版)》v1.5.0
