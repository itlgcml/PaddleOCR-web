#!/usr/bin/env python3
"""
P3C 静态规则检查器
基于正则和结构化分析实现关键规则的自动检测。
LLM 负责汇总报告，此脚本输出结构化 JSON 结果供 LLM 直接引用。
禁止 LLM 跳过此脚本输出自行估算的计数/排名/时间窗数据。
"""

import json
import re
import sys
import os
from pathlib import Path

RESULTS_FILE = os.environ.get("P3C_RESULTS_FILE", ".p3c_checker_results.json")


def is_comment_line(line):
    """检查是否为注释行或属于多行注释。"""
    stripped = line.strip()
    if stripped.startswith("//"):
        return True
    if stripped.startswith("*"):
        return True
    if stripped.startswith("/*"):
        return True
    return False


def check_naming_conventions(source_code):
    """
    检查命名规范（Phase 1）。
    返回 [{"rule": "NM-03", "severity": "P0", "line": N, "code": "...", "message": "..."}]
    """
    findings = []
    lines = source_code.split("\n")
    current_class_start = 0
    in_multiline_comment = False

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # 处理多行注释
        if "/*" in stripped and "*/" not in stripped:
            in_multiline_comment = True
        if "*/" in stripped:
            in_multiline_comment = False
            continue
        if in_multiline_comment or is_comment_line(line):
            continue

        # 检测类定义开始（用于范围限定）
        class_def_match = re.match(r"^\s*(?:public\s+|private\s+|protected\s+)?(?:final\s+|abstract\s+)?class\s+(\w+)", stripped)
        if class_def_match:
            current_class_start = i

        # NM-01/NM-03: 类名必须 UpperCamelCase（正确定义：class 关键字后跟类名）
        # 使用更精确的正则：class 关键字后紧跟空白和类名
        class_match = re.match(r"^\s*(?:public\s+|private\s+|protected\s+)?(?:final\s+|abstract\s+)?class\s+(\w+)", stripped)
        if class_match:
            class_name = class_match.group(1)
            # UpperCamelCase: 首字母大写，不包含下划线
            if not class_name[0].isupper() or "_" in class_name:
                findings.append({
                    "rule": "NM-03", "severity": "P0",
                    "line": i, "code": stripped[:80],
                    "message": f"类名 '{class_name}' 违反 UpperCamelCase 规范"
                })

        # NM-05: 常量全大写下划线分隔（static final）
        const_match = re.match(r"^\s*(?:public\s+|private\s+|protected\s+)?static\s+final\s+(\w+)\s+(\w+)\s*=", stripped)
        if const_match:
            const_type, const_name = const_match.group(1), const_match.group(2)
            # 常见常量类型
            if const_type in ("String", "int", "long", "double", "float", "boolean", "byte", "short", "char"):
                if not re.match(r"^[A-Z][A-Z0-9_]*$", const_name):
                    findings.append({
                        "rule": "NM-05", "severity": "P2",
                        "line": i, "code": stripped[:80],
                        "message": f"常量名 '{const_name}' 未使用全大写下划线分隔"
                    })

        # NM-02: 方法名 lowerCamelCase（排除构造函数）
        # 方法定义：返回类型 + 方法名 + (，且方法名首字母小写或不能是纯大写
        method_match = re.match(r"^\s*(?:public\s+|private\s+|protected\s+)?(?:static\s+)?(?:final\s+)?(?:<[^>]+>\s+)?([A-Z]\w*)\s+(\w+)\s*\(", stripped)
        if method_match:
            return_type, method_name = method_match.group(1), method_match.group(2)
            # 排除构造函数（返回类型和类名相同的情况）
            # 简化的 lowerCamelCase 检查：首字母小写
            if method_name and method_name[0].isupper():
                findings.append({
                    "rule": "NM-02", "severity": "P0",
                    "line": i, "code": stripped[:80],
                    "message": f"方法名 '{method_name}' 违反 lowerCamelCase 规范"
                })

        # NM-04: 枚举成员全大写下划线
        enum_match = re.search(r"enum\s+(\w+)\s*\{([^}]*)\}", stripped)
        if enum_match:
            enum_body = enum_match.group(2)
            for enum_const in re.findall(r"(\w+)", enum_body):
                if enum_const and not re.match(r"^[A-Z][A-Z0-9_]*$", enum_const) and enum_const not in ("public", "private", "protected", "static", "final"):
                    findings.append({
                        "rule": "NM-04", "severity": "P0",
                        "line": i, "code": stripped[:80],
                        "message": f"枚举成员 '{enum_const}' 必须全大写下划线分隔"
                    })

        # NM-06: 抽象类名以 Abstract/Base 开头
        abstract_match = re.match(r"^\s*(?:public\s+|private\s+|protected\s+)?abstract\s+class\s+(\w+)", stripped)
        if abstract_match:
            class_name = abstract_match.group(1)
            if not (class_name.startswith("Abstract") or class_name.startswith("Base")):
                findings.append({
                    "rule": "NM-06", "severity": "P0",
                    "line": i, "code": stripped[:80],
                    "message": f"抽象类 '{class_name}' 应以 Abstract 或 Base 开头"
                })

        # NM-08: POJO 布尔字段不加 is 前缀（基本类型和包装类型都要检查）
        bool_match = re.match(r"^\s*(?:private|public|protected)?\s*(?:Boolean|boolean)\s+(is\w+)", stripped)
        if bool_match:
            findings.append({
                "rule": "NM-08", "severity": "P1",
                "line": i, "code": stripped[:80],
                "message": f"POJO 布尔字段 '{bool_match.group(1)}' 不应加 is 前缀，否则部分框架解析会引起序列化错误"
            })

    return findings


def check_oop_rules(source_code):
    """
    检查 OOP 规范（Phase 2）。
    """
    findings = []
    lines = source_code.split("\n")
    in_multiline_comment = False

    # 用于跟踪当前类的信息
    # 使用类名作为键，因为行号可能会因为内部类等原因变化
    current_class_info = None  # {"name": class_name, "has_hashcode": bool, "has_equals": bool, "equals_line": int, "brace_depth": int}

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # 处理多行注释
        if "/*" in stripped and "*/" not in stripped:
            in_multiline_comment = True
        if "*/" in stripped:
            in_multiline_comment = False
            continue
        if in_multiline_comment or is_comment_line(line):
            continue

        # 检测类定义
        class_def_match = re.match(r"^\s*(?:public\s+|private\s+|protected\s+)?(?:final\s+|abstract\s+)?class\s+(\w+)", stripped)
        if class_def_match:
            # 先处理上一个类（如果有的话）
            if current_class_info and current_class_info["has_equals"] and not current_class_info["has_hashcode"]:
                findings.append({
                    "rule": "COL-01", "severity": "P1",
                    "line": current_class_info["equals_line"],
                    "code": f"class {current_class_info['name']}",
                    "message": f"类 '{current_class_info['name']}' 重写 equals 但未重写 hashCode，违反 hashCode 契约，可能导致对象在集合中行为异常"
                })

            class_name = class_def_match.group(1)
            brace_depth = stripped.count("{") - stripped.count("}")
            current_class_info = {
                "name": class_name,
                "has_hashcode": False,
                "has_equals": False,
                "equals_line": 0,
                "brace_depth": brace_depth
            }
            continue

        # 跟踪类的大括号深度
        if current_class_info:
            current_class_info["brace_depth"] += stripped.count("{") - stripped.count("}")
            if current_class_info["brace_depth"] <= 0:
                # 类结束了，检查结果
                if current_class_info["has_equals"] and not current_class_info["has_hashcode"]:
                    findings.append({
                        "rule": "COL-01", "severity": "P1",
                        "line": current_class_info["equals_line"],
                        "code": f"class {current_class_info['name']}",
                        "message": f"类 '{current_class_info['name']}' 重写 equals 但未重写 hashCode，违反 hashCode 契约，可能导致对象在集合中行为异常"
                    })
                current_class_info = None

        # OOP-04: 包装类用 equals 比较（不能用 ==）
        # 简化的检测：检测包装类类型的变量被 == 或 != 比较
        if re.search(r"\b\w+\s*(==|!=)\s*\w+\b", stripped):
            # 包装类的声明行检测（变量在前几行声明为包装类型）
            # 简化方案：如果行中有包装类定义的关键字且包含 ==/!=
            if re.search(r"\b(Integer|Long|Double|Float|Boolean|Byte|Short|Character)\b", stripped) or \
               re.search(r"\b(int|long|double|float|boolean)\s+\w+.*?(==|!=)", stripped):
                # 排除 null 比较和赋值
                line_without_comments = stripped.split("//")[0]
                if "null" not in line_without_comments.lower():
                    # 排除标准赋值：Xxx yyy = ...
                    if not re.match(r"^\s*(?:final\s+)?(?:Integer|Long|Double|Float|Boolean|int|long|double|float|boolean)\s+\w+\s*=", line_without_comments):
                        findings.append({
                            "rule": "OOP-04", "severity": "P0",
                            "line": i, "code": stripped[:80],
                            "message": "疑似包装类使用 ==/!= 比较值，应使用 equals() 方法"
                        })

        # OOP-05: POJO 属性用包装类型
        primitive_match = re.search(r"\b(?:private|public|protected)\s+(?:int|long|double|float|boolean|byte|short|char)\s+\w+;", stripped)
        if primitive_match:
            findings.append({
                "rule": "OOP-05", "severity": "P1",
                "line": i, "code": stripped[:80],
                "message": "POJO 属性建议使用包装类型而非基本类型，数据库字段可能为 null"
            })

        # 跟踪 hashCode 重写
        if re.search(r"public\s+int\s+hashCode\s*\(", stripped):
            if current_class_info:
                current_class_info["has_hashcode"] = True

        # 跟踪 equals 重写
        if re.search(r"public\s+boolean\s+equals\s*\(\s*Object", stripped):
            if current_class_info:
                current_class_info["has_equals"] = True
                current_class_info["equals_line"] = i

        # COL-05: foreach 循环中禁止 remove/add
        foreach_match = re.search(r"for\s*\(\s*(?:final\s+)?(\w+)\s+(\w+)\s*:\s*(\w+)\s*\)", stripped)
        if foreach_match:
            var_type, var_name, collection_name = foreach_match.groups()
            brace_count = stripped.count("{") - stripped.count("}")
            found_violation = False
            for j in range(i, min(i + 20, len(lines))):
                if found_violation:
                    break
                inner_line = lines[j]
                inner_stripped = inner_line.strip()

                # 检查是否调用了集合的 remove/add
                if re.search(rf"\b{collection_name}\s*\.\s*(remove|add)\s*\(", inner_stripped):
                    if not inner_stripped.startswith("//"):
                        findings.append({
                            "rule": "COL-05", "severity": "P0",
                            "line": j + 1, "code": inner_line.strip()[:80],
                            "message": f"foreach 循环中禁止调用集合的 {collection_name}.remove/add 方法，会导致 ConcurrentModificationException"
                        })
                        found_violation = True

                brace_count += inner_line.count("{") - inner_line.count("}")
                if brace_count <= 0 and j > i:
                    break

    # 处理最后一个类
    if current_class_info and current_class_info["has_equals"] and not current_class_info["has_hashcode"]:
        findings.append({
            "rule": "COL-01", "severity": "P1",
            "line": current_class_info["equals_line"],
            "code": f"class {current_class_info['name']}",
            "message": f"类 '{current_class_info['name']}' 重写 equals 但未重写 hashCode，违反 hashCode 契约，可能导致对象在集合中行为异常"
        })

    return findings


def check_concurrency_rules(source_code):
    """
    检查并发规范（Phase 3）。
    """
    findings = []
    lines = source_code.split("\n")

    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*"):
            continue

        # CON-01: 线程池必须用 ThreadPoolExecutor 创建
        if "Executors." in stripped and ("newFixedThreadPool" in stripped or "newCachedThreadPool" in stripped or "newSingleThreadExecutor" in stripped):
            findings.append({
                "rule": "CON-01", "severity": "P0",
                "line": i, "code": stripped[:80],
                "message": "线程池必须使用 ThreadPoolExecutor 创建，禁止Executors工厂方法"
            })

        # CON-03: SimpleDateFormat 不能用 static
        if re.search(r"private\s+static\s+.*SimpleDateFormat", stripped) or re.search(r"private\s+final\s+.*SimpleDateFormat", stripped):
            findings.append({
                "rule": "CON-03", "severity": "P0",
                "line": i, "code": stripped[:80],
                "message": "SimpleDateFormat 不能使用 static 或成员变量，存在线程安全问题"
            })

        # EXP-05: finally 块中不能有 return
        if "finally" in stripped and "return" in stripped:
            findings.append({
                "rule": "EXP-05", "severity": "P0",
                "line": i, "code": stripped[:80],
                "message": "finally 块中禁止使用 return，会吞掉异常"
            })

    return findings


def check_exception_logging(source_code):
    """
    检查异常处理、日志规约和安全规约（Phase 5）。
    EX: 异常处理规约
    LOG: 日志规约
    SEC: 安全规约（除SQL注入外的其他安全检查）
    """
    findings = []
    lines = source_code.split("\n")

    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*"):
            continue

        # LOG-01: 使用 SLF4J 而非 Log4j/Log4j2
        if "import org.apache.log4j" in stripped or "import org.apache.logging.log4j" in stripped:
            findings.append({
                "rule": "LOG-01", "severity": "P1",
                "line": i, "code": stripped[:80],
                "message": "日志框架应使用 SLF4J 而非 Log4j/Log4j2，便于统一日志输出"
            })

        # LOG-02: 异常必须打印堆栈（检查 logger.error(e) 形式）
        if re.search(r"logger\.(?:error|warn|info)\s*\(\s*\w+Exception\s*\)", stripped):
            findings.append({
                "rule": "LOG-02", "severity": "P1",
                "line": i, "code": stripped[:80],
                "message": "捕获异常后打印日志应包含堆栈信息，避免 logger.error(e)，应使用 logger.error(\"message\", e)"
            })

        # EX-01: 避免用 RuntimeException 规避预检查异常
        if "throw new RuntimeException" in stripped and "catch" in lines[max(0, i-3):i]:
            findings.append({
                "rule": "EX-01", "severity": "P1",
                "line": i, "code": stripped[:80],
                "message": "禁止在 catch 中直接 throw new RuntimeException() 吞掉原始异常信息"
            })

        # SEC-04: 用户输入必须校验
        if re.search(r"\.getParameter\s*\(", stripped) and i < len(lines):
            # 检查后续3行是否有校验逻辑
            next_lines = "\n".join(lines[i:min(i+3, len(lines))])
            if not any(x in next_lines for x in ["!= null", "isNotBlank", "isNotEmpty", "validate", "check"]):
                findings.append({
                    "rule": "SEC-04", "severity": "P0",
                    "line": i, "code": stripped[:80],
                    "message": "获取用户输入参数后应进行有效性校验，防止恶意输入"
                })

    return findings


def check_controller_architecture(source_code):
    """
    检查 Controller 分层架构违规（Phase 4）。
    ENG-01: Controller 禁止直接操作数据库。

    简化策略：逐行检测，使用大括号跟踪当前类。
    一旦检测到 class XxxController {，就进入 Controller 模式
    直到对应的大括号闭合。
    """
    findings = []
    lines = source_code.split("\n")
    brace_depth = 0
    controller_at_depth = None  # 记录 Controller 类开始的深度

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # 跳过空行和注释行
        if not stripped or stripped.startswith("//") or stripped.startswith("*"):
            continue

        # 在当前深度检测类定义
        if controller_at_depth is None or brace_depth == controller_at_depth:
            class_match = re.search(r"\bclass\s+(\w+)", stripped)
            if class_match:
                class_name = class_match.group(1)
                if class_name.endswith("Controller"):
                    controller_at_depth = brace_depth

        # 如果在 Controller 内部，检查数据库操作
        if controller_at_depth is not None and brace_depth >= controller_at_depth:
            class_name = "Controller"  # 简化输出
            db_ops = ["DriverManager", "DataSource", "JdbcTemplate",
                     "getConnection(", ".execute(", ".query(", ".update("]
            for op in db_ops:
                if op in stripped:
                    findings.append({
                        "rule": "ENG-01", "severity": "P0",
                        "line": i, "code": stripped[:80],
                        "message": f"Controller 直接操作数据库（{op}），违反分层架构规范"
                    })
                    break

        # 更新大括号深度
        brace_depth += stripped.count("{") - stripped.count("}")

        # 如果已经退出 Controller 类
        if controller_at_depth is not None and brace_depth <= controller_at_depth:
            controller_at_depth = None

    return findings


def check_sql_injection(source_code):
    """
    检查 SQL 注入风险（Phase 5/6）。
    SQL-15: 使用 #{} 而非 ${}
    SEC-03: SQL 参数绑定防注入（使用 PreparedStatement）
    """
    findings = []
    lines = source_code.split("\n")

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # SQL-15: ${} 存在注入风险
        if "${" in stripped and not "#{" in stripped:
            if any(x in stripped for x in ["SELECT", "INSERT", "UPDATE", "DELETE", "sql", "SQL"]):
                findings.append({
                    "rule": "SQL-15", "severity": "P0",
                    "line": i, "code": stripped[:80],
                    "message": "SQL 语句中使用 ${} 而非 #{}，存在 SQL 注入风险"
                })

        # SEC-03: SQL 字符串拼接（潜在注入风险）
        if re.search(r"String\s+(?:sql|query)\s*=.*\+.*\+", stripped):
            if any(x in stripped for x in ["SELECT", "INSERT", "UPDATE", "DELETE"]):
                findings.append({
                    "rule": "SEC-03", "severity": "P0",
                    "line": i, "code": stripped[:80],
                    "message": "SQL 语句使用字符串拼接，存在 SQL 注入风险，应使用 PreparedStatement 和参数绑定"
                })

    return findings


def run_checks(source_code, phase=None):
    """
    主入口：运行指定阶段（phase 1~6）的所有检查。
    phase=None 表示全阶段。
    返回 {"phase": N, "total_findings": N, "by_severity": {...}, "findings": [...]}

    阶段划分：
    - Phase 1: 命名规范 - check_naming_conventions (NM, CST, FMT)
    - Phase 2: OOP与集合 - check_oop_rules (OOP, COL)
    - Phase 3: 并发与控制 - check_concurrency_rules (CON, CTL, EXP)
    - Phase 4: 工程结构 - check_controller_architecture (ENG)
    - Phase 5: 异常日志安全 - check_exception_logging (EX, LOG, SEC)
    - Phase 6: MySQL与ORM - check_sql_injection (SQL, ORM)
    """
    all_findings = []

    if phase is None or phase == 1:
        all_findings.extend(check_naming_conventions(source_code))
    if phase is None or phase == 2:
        all_findings.extend(check_oop_rules(source_code))
    if phase is None or phase == 3:
        all_findings.extend(check_concurrency_rules(source_code))
    if phase is None or phase == 4:
        all_findings.extend(check_controller_architecture(source_code))
    if phase is None or phase == 5:
        # Phase 5: 日志规约、异常处理、安全规约
        all_findings.extend(check_exception_logging(source_code))
    if phase is None or phase == 6:
        # Phase 6: MySQL规约、ORM规约
        all_findings.extend(check_sql_injection(source_code))

    by_severity = {"P0": 0, "P1": 0, "P2": 0}
    for f in all_findings:
        sev = f.get("severity", "P2")
        if sev in by_severity:
            by_severity[sev] += 1
        else:
            by_severity["P2"] += 1

    result = {
        "phase": phase if phase else "all",
        "total_findings": len(all_findings),
        "by_severity": by_severity,
        "findings": all_findings
    }

    # 使用临时文件+原子重命名确保写入完整性
    import tempfile
    temp_fd = None
    temp_path = None
    try:
        # 创建临时文件到同一目录，确保原子重命名有效
        results_dir = os.path.dirname(RESULTS_FILE) or "."
        temp_fd, temp_path = tempfile.mkstemp(
            dir=results_dir,
            prefix=".p3c_results_tmp_",
            suffix=".json"
        )
        with os.fdopen(temp_fd, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
            temp_fd = None  # 文件已关闭
        # 原子重命名
        os.rename(temp_path, RESULTS_FILE)
    except (IOError, PermissionError, OSError) as e:
        error_msg = f"❌ [错误] 结果文件写入失败: {e}"
        print(error_msg, file=sys.stderr)
        # 清理临时文件
        if temp_fd is not None:
            try:
                os.close(temp_fd)
            except:
                pass
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except:
                pass
        # 返回非零退出码
        sys.exit(2)

    return result


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: p3c_checker.py <源文件路径> [phase]")
        sys.exit(1)

    file_path = sys.argv[1]
    phase = int(sys.argv[2]) if len(sys.argv) >= 3 else None

    if not os.path.exists(file_path):
        print(f"错误: 文件不存在: {file_path}")
        sys.exit(1)

    with open(file_path, "r", encoding="utf-8") as f:
        source_code = f.read()

    result = run_checks(source_code, phase)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"\n结果已写入 {RESULTS_FILE}，请在审查报告中逐字引用上述 JSON，禁用估算数据。")