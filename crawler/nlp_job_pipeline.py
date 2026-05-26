"""招聘数据 NLP 处理流水线。

功能概览：
- 从 MySQL 读取 job_info / job_info_51job
- 文本清洗与分词（支持停用词、技能白名单、核心特征 text_core）
- 导出清洗数据、统计报表（Top Tokens）、TF-IDF 特征、聚类可视化
- 可选：训练薪资分档模型（MLP / TextCNN）

运行方式：python crawler/nlp_job_pipeline.py <cmd>
- preprocess / export-clean / export-features / stats / cluster / train-mlp / train-textcnn / viz / dashboard

输出位置：
- --output-dir 默认 crawler/output/run_时间戳/
- --raw-dir 默认 crawler/data/raw/
- --text-features-dir 默认 crawler/data/text_features/
"""

import argparse
import json
import os
from datetime import datetime
from typing import Dict, List, Optional

import pandas as pd

from nlp_ml import train_mlp_salary_level, train_textcnn_salary_level
from nlp_preprocess import (
    DbConfig,
    attach_tokens,
    clean_jobs_df,
    export_cleaned_to_raw_dir,
    read_jobs_from_mysql,
    reduce_dataframe,
)
from nlp_viz import export_job_skill_heatmap, export_skill_timeline, export_stats, run_cluster
from nlp_viz import export_company_size_salary_bar
from nlp_viz import export_edu_exp_salary_bubble


def _project_root() -> str:
    """返回项目根目录的绝对路径（crawler 的上级目录）。"""
    return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def _runtime_config_path() -> str:
    """返回运行时配置文件的路径，优先使用环境变量 JOBDATA_RUNTIME_CONFIG，否则默认为 crawler 目录下的 runtime_config.json。"""
    return os.environ.get("JOBDATA_RUNTIME_CONFIG") or os.path.join(os.path.dirname(__file__), "runtime_config.json")


def _default_output_dir() -> str:
    """返回默认输出目录路径：crawler/output/。"""
    return os.path.join(os.path.dirname(__file__), "output")


def _default_raw_dir() -> str:
    """返回默认原始数据导出目录路径：crawler/data/raw/。"""
    return os.path.join(os.path.dirname(__file__), "data", "raw")


def _default_text_features_dir() -> str:
    """返回默认文本特征目录路径：crawler/data/text_features/。"""
    return os.path.join(os.path.dirname(__file__), "data", "text_features")


def _now_tag() -> str:
    """返回当前时间的格式化字符串，用于生成运行目录名（如 20260526_143730）。"""
    return datetime.now().strftime("%Y%m%d_%H%M%S")

def _load_runtime_config(path: str) -> Dict[str, object]:
    """加载 JSON 格式的运行时配置文件，校验格式后返回解析后的字典。"""
    p = os.path.abspath(str(path or "").strip())
    if not p or not os.path.exists(p):
        raise RuntimeError(f"找不到配置文件: {p}")
    with open(p, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise RuntimeError("配置文件格式错误: 需要 JSON Object")
    return data


def build_arg_parser() -> argparse.ArgumentParser:
    """构建命令行参数解析器，定义所有子命令（preprocess/stats/cluster/dashboard 等）及其参数。"""
    p = argparse.ArgumentParser(prog="nlp_job_pipeline.py")
    p.add_argument("--runtime-config", default=_runtime_config_path())
    p.add_argument("--db-host", default=None)
    p.add_argument("--db-port", type=int, default=None)
    p.add_argument("--db-user", default=None)
    p.add_argument("--db-password", default=None)
    p.add_argument("--db-name", default=None)
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--output-dir", default=_default_output_dir())
    p.add_argument("--raw-dir", default=_default_raw_dir())
    p.add_argument("--text-features-dir", default=_default_text_features_dir())
    p.add_argument("--removed-terms", default=os.path.join(_project_root(), "data", "text_features", "removed_terms.txt"))
    p.add_argument("--whitelist", default=os.path.join(_project_root(), "data", "text_features", "skill_whitelist.txt"))
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("preprocess")
    sub.add_parser("export-clean")
    sub.add_parser("stats")

    c = sub.add_parser("cluster")
    c.add_argument("--k", type=int, default=8)
    c.add_argument("--max-features", type=int, default=8000)

    m = sub.add_parser("train-mlp")
    m.add_argument("--max-features", type=int, default=20000)
    m.add_argument("--hidden", type=int, default=256)
    m.add_argument("--epochs", type=int, default=3)

    t = sub.add_parser("train-textcnn")
    t.add_argument("--vocab-size", type=int, default=30000)
    t.add_argument("--max-len", type=int, default=200)
    t.add_argument("--epochs", type=int, default=3)

    d = sub.add_parser("dashboard")
    d.add_argument("--k", type=int, default=8)
    d.add_argument("--cluster-max-features", type=int, default=8000)
    d.add_argument("--mlp-max-features", type=int, default=20000)
    d.add_argument("--mlp-hidden", type=int, default=256)
    d.add_argument("--mlp-epochs", type=int, default=3)
    d.add_argument("--textcnn-vocab-size", type=int, default=30000)
    d.add_argument("--textcnn-max-len", type=int, default=200)
    d.add_argument("--textcnn-epochs", type=int, default=3)
    return p


def _emit_pipeline_json(payload: Dict[str, object]):
    """向前端/后端通过 stdout 输出流水线结果，使用 __PIPELINE_JSON__ 前缀标记便于解析。"""
    print("__PIPELINE_JSON__" + json.dumps(payload, ensure_ascii=False))


def main():
    """流水线主入口：解析命令行参数 → 连接数据库读取数据 → 清洗分词 → 按子命令执行对应任务（统计/聚类/训练/dashboard 等）。"""
    args = build_arg_parser().parse_args()
    rc = _load_runtime_config(getattr(args, "runtime_config", None))
    db = (rc.get("db") or {}) if isinstance(rc, dict) else {}
    if not isinstance(db, dict):
        db = {}

    cfg = DbConfig(
        host=(args.db_host or db.get("host") or "").strip(),
        port=int(args.db_port or db.get("port") or 0),
        user=(args.db_user or db.get("user") or "").strip(),
        password=(args.db_password or db.get("password") or "").strip(),
        database=(args.db_name or db.get("database") or "").strip(),
        charset=(db.get("charset") or "utf8mb4"),
    )

    limit: Optional[int] = int(args.limit) if int(args.limit) > 0 else None
    out_dir = os.path.abspath(args.output_dir)
    os.makedirs(out_dir, exist_ok=True)
    run_dir = os.path.join(out_dir, f"run_{_now_tag()}")
    os.makedirs(run_dir, exist_ok=True)
    raw_dir = os.path.abspath(args.raw_dir)
    text_features_dir = os.path.abspath(args.text_features_dir)

    errors: List[str] = []
    raw = pd.DataFrame()
    cleaned = pd.DataFrame()
    with_tokens = pd.DataFrame()
    reduced = pd.DataFrame()
    raw_export_paths: Dict[str, str] = {}
    try:
        raw = read_jobs_from_mysql(cfg, limit=limit)
        cleaned = clean_jobs_df(raw)
        with_tokens = attach_tokens(cleaned, removed_terms_path=args.removed_terms, whitelist_path=args.whitelist)
        reduced = reduce_dataframe(with_tokens)
        raw_export_paths = export_cleaned_to_raw_dir(
            cfg,
            raw_dir=raw_dir,
            removed_terms_path=args.removed_terms,
            whitelist_path=args.whitelist,
            limit=limit,
        )
    except Exception as e:
        errors.append(str(e))

    p_clean = os.path.join(run_dir, "jobs_clean.csv")
    p_reduced = os.path.join(run_dir, "jobs_reduced.csv")
    cleaned.to_csv(p_clean, index=False, encoding="utf-8-sig")
    reduced.to_csv(p_reduced, index=False, encoding="utf-8-sig")

    base_artifacts: Dict[str, object] = {
        "run_dir": run_dir,
        "artifacts": {
            "jobs_clean_csv": p_clean,
            "jobs_reduced_csv": p_reduced,
        },
    }
    if errors:
        base_artifacts["errors"] = errors
    if raw_export_paths:
        base_artifacts["artifacts"].update(raw_export_paths)

    if args.cmd in {"preprocess", "export-clean"}:
        _emit_pipeline_json(base_artifacts)
        return

    if args.cmd == "stats":
        paths = export_stats(reduced, run_dir)
        base_artifacts["artifacts"].update(paths)
        _emit_pipeline_json(base_artifacts)
        return

    if args.cmd == "cluster":
        paths = run_cluster(
            reduced,
            out_dir=run_dir,
            n_clusters=int(args.k),
            max_features=int(args.max_features),
        )
        base_artifacts["artifacts"].update(paths)
        _emit_pipeline_json(base_artifacts)
        return

    if args.cmd == "train-mlp":
        paths = train_mlp_salary_level(
            with_tokens,
            out_dir=run_dir,
            max_features=int(args.max_features),
            hidden=int(args.hidden),
            epochs=int(args.epochs),
        )
        base_artifacts["artifacts"].update(paths)
        _emit_pipeline_json(base_artifacts)
        return

    if args.cmd == "train-textcnn":
        paths = train_textcnn_salary_level(
            with_tokens,
            out_dir=run_dir,
            vocab_size=int(args.vocab_size),
            max_len=int(args.max_len),
            epochs=int(args.epochs),
        )
        base_artifacts["artifacts"].update(paths)
        _emit_pipeline_json(base_artifacts)
        return

    if args.cmd == "dashboard":
        if reduced.empty:
            base_artifacts.setdefault("errors", []).append("无可用数据：请先运行爬虫并确保 job_info / job_info_51job 有数据。")
            _emit_pipeline_json(base_artifacts)
            return

        try:
            base_artifacts["artifacts"].update(export_stats(reduced, run_dir))
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("stats 失败: " + str(e))

        try:
            base_artifacts["artifacts"].update(
                run_cluster(
                    reduced,
                    out_dir=run_dir,
                    n_clusters=int(args.k),
                    max_features=int(args.cluster_max_features),
                )
            )
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("cluster 失败: " + str(e))

        try:
            base_artifacts["artifacts"].update(export_job_skill_heatmap(with_tokens, run_dir))
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("heatmap 失败: " + str(e))

        timeline_dir = os.path.join(run_dir, "timeline")
        try:
            base_artifacts["artifacts"].update(export_skill_timeline(with_tokens, timeline_dir, top_skills=10))
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("timeline 失败: " + str(e))

        size_salary_dir = os.path.join(run_dir, "stats")
        try:
            base_artifacts["artifacts"].update(export_company_size_salary_bar(with_tokens, size_salary_dir, min_count=10))
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("company_size_salary 失败: " + str(e))

        edu_exp_dir = os.path.join(run_dir, "stats")
        try:
            base_artifacts["artifacts"].update(export_edu_exp_salary_bubble(with_tokens, edu_exp_dir, min_count=5))
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("edu_exp_salary_bubble 失败: " + str(e))

        try:
            base_artifacts["artifacts"].update(
                train_mlp_salary_level(
                    with_tokens,
                    out_dir=run_dir,
                    max_features=int(args.mlp_max_features),
                    hidden=int(args.mlp_hidden),
                    epochs=int(args.mlp_epochs),
                )
            )
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("train-mlp 失败: " + str(e))

        try:
            base_artifacts["artifacts"].update(
                train_textcnn_salary_level(
                    with_tokens,
                    out_dir=run_dir,
                    vocab_size=int(args.textcnn_vocab_size),
                    max_len=int(args.textcnn_max_len),
                    epochs=int(args.textcnn_epochs),
                )
            )
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("train-textcnn 失败: " + str(e))

        _emit_pipeline_json(base_artifacts)
        return


if __name__ == "__main__":
    main()
