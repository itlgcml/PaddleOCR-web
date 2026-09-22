#!/usr/bin/env python3
"""
P3C 代码审查状态管理器
负责审查进度的持久化与恢复，支持 --continue 断点续审和 --status 状态查询。
状态文件路径：.p3c_review_state.json
"""

import json
import os
import sys
import tempfile
from pathlib import Path
from datetime import datetime

STATE_FILE = os.environ.get("P3C_STATE_FILE", ".p3c_review_state.json")
MAX_PHASE = 6


def load_state():
    """加载状态文件，若不存在则返回空状态。"""
    if not os.path.exists(STATE_FILE):
        return {"current_phase": 1, "max_phase": MAX_PHASE, "files_checked": [], "findings": [], "last_updated": None}
    try:
        with open(STATE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, IOError):
        return {"current_phase": 1, "max_phase": MAX_PHASE, "files_checked": [], "findings": [], "last_updated": None}


def save_state(state):
    """将状态写入 .p3c_review_state.json，失败时保留内存状态并告警。使用原子写入确保完整性。"""
    state["last_updated"] = datetime.now().isoformat()
    temp_fd = None
    temp_path = None
    try:
        # 创建临时文件到同一目录，确保原子重命名有效
        state_dir = os.path.dirname(STATE_FILE) or "."
        temp_fd, temp_path = tempfile.mkstemp(
            dir=state_dir,
            prefix=".p3c_state_tmp_",
            suffix=".json"
        )
        with os.fdopen(temp_fd, "w", encoding="utf-8") as f:
            json.dump(state, f, ensure_ascii=False, indent=2)
            temp_fd = None  # 文件已关闭
        # 原子重命名
        os.rename(temp_path, STATE_FILE)
        return True
    except (IOError, PermissionError, OSError) as e:
        # 错误处理：保留内存状态并输出告警
        error_msg = f"⚠️ [状态管理] 状态文件写入失败: {e}. 审查将继续，但断点续审功能将不可用。"
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
        # 返回 False 让调用者知道写入失败
        return False


def init_review(target_file):
    """初始化新审查，清空上一次状态。"""
    state = {
        "current_phase": 1,
        "max_phase": MAX_PHASE,
        "target_file": target_file,
        "files_checked": [],
        "findings": [],
        "phase_summaries": {},
        "last_updated": datetime.now().isoformat()
    }
    save_state(state)
    return state


def advance_phase():
    """将 current_phase 加 1，支持断点续审。"""
    state = load_state()
    if state["current_phase"] < state["max_phase"]:
        state["current_phase"] += 1
        save_state(state)
    return state


def record_phase_result(phase, summary):
    """
    记录单个阶段的检查结果摘要。

    summary 格式示例：
    {
        "phase": 1,
        "status": "completed",        # completed | interrupted | failed
        "problem_count": {"P0": 0, "P1": 2, "P2": 1},
        "problem_types": ["NM-03", "NM-05"],
        "files_checked": ["UserService.java"],
        "findings": [
            {"rule": "NM-03", "severity": "P0", "description": "类名违反 UpperCamelCase"}
        ]
    }
    """
    state = load_state()
    state["phase_summaries"][str(phase)] = summary
    state["current_phase"] = phase
    # Fix: 从 summary 中获取 files_checked，而不是添加 phase 整数
    summary_files = summary.get("files_checked", [])
    for f in summary_files:
        if f not in state["files_checked"]:
            state["files_checked"].append(f)
    save_state(state)


def add_finding(finding):
    """
    添加一条发现问题。

    finding 格式：
    {"rule": "OOP-04", "severity": "P0", "file": "UserService.java",
     "line": 42, "description": "使用 == 比较包装类"}
    """
    state = load_state()
    state["findings"].append(finding)
    save_state(state)


def get_status_text():
    """返回格式化的状态摘要，用于 --status 输出。"""
    state = load_state()
    lines = [
        f"目标文件: {state.get('target_file', 'N/A')}",
        f"当前阶段: Phase {state['current_phase']} / {state['max_phase']}",
        f"最后更新: {state.get('last_updated', 'N/A')}",
        f"已检查文件数: {len(state.get('files_checked', []))}",
        f"发现问题数: {len(state.get('findings', []))}",
    ]
    if state.get("phase_summaries"):
        lines.append("\n各阶段摘要:")
        for ph, s in sorted(state["phase_summaries"].items()):
            lines.append(f"  Phase {ph}: {s.get('status', 'N/A')} | "
                        f"P0={s.get('problem_count', {}).get('P0', 0)} "
                        f"P1={s.get('problem_count', {}).get('P1', 0)} "
                        f"P2={s.get('problem_count', {}).get('P2', 0)}")
    return "\n".join(lines)


def clear_state():
    """清除状态文件，用于新审查初始化前的清理或中断回滚。"""
    if os.path.exists(STATE_FILE):
        try:
            os.remove(STATE_FILE)
            return True
        except (IOError, PermissionError, OSError):
            return False
    return True


def cleanup_temp_files():
    """清理所有 P3C 相关的临时文件，用于审查中断或回滚。"""
    state_dir = os.path.dirname(STATE_FILE) or "."
    patterns = [
        ".p3c_state_tmp_*.json",
        ".p3c_results_tmp_*.json",
        ".p3c_checker_results.json",
        ".p3c_review_state.json",
        "p3c_review_report.md"
    ]
    import fnmatch
    cleaned = []
    failed = []
    try:
        for fname in os.listdir(state_dir):
            for pattern in patterns:
                if fnmatch.fnmatch(fname, pattern):
                    fpath = os.path.join(state_dir, fname)
                    try:
                        os.remove(fpath)
                        cleaned.append(fname)
                    except (IOError, PermissionError, OSError):
                        failed.append(fname)
    except (IOError, PermissionError, OSError):
        pass
    return cleaned, failed


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: state_manager.py <init|status|advance|record> [参数]")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "status":
        print(get_status_text())
    elif cmd == "init" and len(sys.argv) >= 3:
        init_review(sys.argv[2])
        print(f"审查已初始化，目标文件: {sys.argv[2]}")
    elif cmd == "advance":
        state = advance_phase()
        print(f"已推进至 Phase {state['current_phase']}")
    elif cmd == "record" and len(sys.argv) >= 4:
        phase = int(sys.argv[2])
        summary_json = " ".join(sys.argv[3:])
        try:
            summary = json.loads(summary_json)
        except json.JSONDecodeError:
            print("错误: summary 参数必须是合法 JSON 字符串")
            sys.exit(1)
        record_phase_result(phase, summary)
        print(f"Phase {phase} 结果已记录")
    elif cmd == "clear":
        if clear_state():
            print("状态已清除")
        else:
            print("⚠️ 状态文件清除失败，可能需要手动删除", file=sys.stderr)
            sys.exit(1)
    elif cmd == "cleanup":
        cleaned, failed = cleanup_temp_files()
        if cleaned:
            print(f"已清理临时文件: {', '.join(cleaned)}")
        if failed:
            print(f"⚠️ 以下文件清理失败: {', '.join(failed)}", file=sys.stderr)
        if not cleaned and not failed:
            print("未发现需要清理的临时文件")
    else:
        print("未知命令或参数不足")
        print("用法: state_manager.py <init|status|advance|record|clear|cleanup> [参数]")
        sys.exit(1)