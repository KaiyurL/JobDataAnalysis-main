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
import re
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import jieba
import pandas as pd
import pymysql


@dataclass(frozen=True)
class DbConfig:
    host: str = "localhost"
    port: int = 3306
    user: str = "root"
    password: str = "123456ppoo"
    database: str = "job_data"
    charset: str = "utf8mb4"

    def to_pymysql_kwargs(self) -> Dict[str, object]:
        return {
            "host": self.host,
            "port": int(self.port),
            "user": self.user,
            "password": self.password,
            "database": self.database,
            "charset": self.charset,
        }


def _project_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def _default_output_dir() -> str:
    return os.path.join(os.path.dirname(__file__), "output")


def _default_raw_dir() -> str:
    return os.path.join(os.path.dirname(__file__), "data", "raw")

def _default_text_features_dir() -> str:
    return os.path.join(os.path.dirname(__file__), "data", "text_features")


def _now_tag() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def _read_lines(path: str) -> List[str]:
    if not path or not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        return [line.strip() for line in f.readlines()]


def load_removed_terms(removed_terms_path: str) -> List[str]:
    terms = []
    for line in _read_lines(removed_terms_path):
        if not line or line.startswith("关键词") or line.startswith("-"):
            continue
        parts = re.split(r"\s+", line)
        if parts:
            t = parts[0].strip()
            if t:
                terms.append(t)
    return terms


def load_whitelist(whitelist_path: str) -> List[str]:
    items = []
    for line in _read_lines(whitelist_path):
        t = str(line or "").strip().lower()
        if t:
            items.append(t)
    return items


def connect_mysql(cfg: DbConfig):
    return pymysql.connect(**cfg.to_pymysql_kwargs())


def read_jobs_from_mysql(
    cfg: DbConfig,
    tables: Sequence[str] = ("job_info", "job_info_51job"),
    limit: Optional[int] = None,
) -> pd.DataFrame:
    """从 MySQL 读取招聘数据。

    - tables: 需要读取的表名列表（默认 boss=job_info + 51job=job_info_51job）
    - limit: 仅用于调试/抽样

    返回 DataFrame，并额外写入一列 source_table 标记数据来源表。
    """

    frames = []
    conn = connect_mysql(cfg)
    try:
        for table in tables:
            sql = f"""
                SELECT
                    id,
                    job_name,
                    company_name,
                    city,
                    job_url,
                    salary_min,
                    salary_max,
                    salary_avg,
                    experience,
                    education,
                    job_desc,
                    job_keywords,
                    company_size,
                    company_industry,
                    company_welfare,
                    publish_date,
                    created_at
                FROM {table}
            """
            if limit is not None and int(limit) > 0:
                sql += f" LIMIT {int(limit)}"
            df = pd.read_sql_query(sql, conn)
            df["source_table"] = table
            frames.append(df)
    finally:
        conn.close()

    if not frames:
        return pd.DataFrame()

    out = pd.concat(frames, ignore_index=True)
    return out


def export_cleaned_to_raw_dir(
    cfg: DbConfig,
    raw_dir: str,
    removed_terms_path: str,
    whitelist_path: Optional[str],
    limit: Optional[int] = None,
) -> Dict[str, str]:
    """导出按来源拆分的清洗数据（覆盖写入 raw_dir）。

    会为每张来源表生成两份文件：
    - *_clean.csv：基础字段清洗后的结果
    - *_clean_nlp.csv：在 clean 基础上附加 text_raw/tokens/text/tokens_core/text_core 等 NLP 字段

    返回值是一个 {key: path} 的映射，便于上层汇总到 artifacts。
    """

    os.makedirs(raw_dir, exist_ok=True)

    paths: Dict[str, str] = {}
    for table in ["job_info", "job_info_51job"]:
        raw_df = read_jobs_from_mysql(cfg, tables=(table,), limit=limit)
        clean_df = clean_jobs_df(raw_df)
        clean_df["source_table"] = table
        with_tokens = attach_tokens(clean_df, removed_terms_path=removed_terms_path, whitelist_path=whitelist_path)

        base = "boss" if table == "job_info" else "51job"
        p_clean = os.path.join(raw_dir, f"{base}_clean.csv")
        p_nlp = os.path.join(raw_dir, f"{base}_clean_nlp.csv")

        clean_df.to_csv(p_clean, index=False, encoding="utf-8-sig")

        nlp_df = with_tokens.copy()
        if "tokens" in nlp_df.columns:
            nlp_df["tokens"] = nlp_df["tokens"].map(lambda xs: " ".join(xs) if isinstance(xs, list) else str(xs or ""))
        if "tokens_skill" in nlp_df.columns:
            nlp_df["tokens_skill"] = nlp_df["tokens_skill"].map(lambda xs: " ".join(xs) if isinstance(xs, list) else str(xs or ""))
        if "tokens_core" in nlp_df.columns:
            nlp_df["tokens_core"] = nlp_df["tokens_core"].map(lambda xs: " ".join(xs) if isinstance(xs, list) else str(xs or ""))
        nlp_df.to_csv(p_nlp, index=False, encoding="utf-8-sig")

        paths[f"{base}_clean"] = p_clean
        paths[f"{base}_clean_nlp"] = p_nlp

    return paths


def export_text_features(
    cfg: DbConfig,
    text_features_dir: str,
    removed_terms_path: str,
    whitelist_path: Optional[str],
    limit: Optional[int] = None,
    max_features: int = 20000,
    ngram_range: Tuple[int, int] = (1, 2),
    min_df: int = 2,
    max_df: float = 0.98,
) -> Dict[str, str]:
    _require_sklearn()
    os.makedirs(text_features_dir, exist_ok=True)

    from sklearn.feature_extraction.text import TfidfVectorizer
    from scipy import sparse

    results: Dict[str, str] = {}

    def build(prefix: str, df_in: pd.DataFrame) -> None:
        if df_in is None or df_in.empty:
            return

        cleaned = clean_jobs_df(df_in)
        with_tokens = attach_tokens(cleaned, removed_terms_path=removed_terms_path, whitelist_path=whitelist_path)

        for col in ["text", "text_skill"]:
            if col not in with_tokens.columns:
                continue
            texts = with_tokens[col].astype(str).fillna("").tolist()
            if not any(t.strip() for t in texts):
                continue

            vec = TfidfVectorizer(
                analyzer="word",
                token_pattern=r"(?u)\b\w+\b",
                lowercase=False,
                max_features=int(max_features),
                ngram_range=tuple(ngram_range),
                min_df=int(min_df),
                max_df=float(max_df),
                sublinear_tf=True,
            )
            X = vec.fit_transform(texts)

            out_subdir = os.path.join(text_features_dir, prefix)
            os.makedirs(out_subdir, exist_ok=True)
            suffix = "full" if col == "text" else "skill"

            p_npz = os.path.join(out_subdir, f"tfidf_matrix_{suffix}.npz")
            sparse.save_npz(p_npz, X.tocsr())
            results[f"{prefix}_tfidf_matrix_{suffix}"] = p_npz

            vocab = {str(k): int(v) for k, v in (vec.vocabulary_ or {}).items()}
            p_vocab = os.path.join(out_subdir, f"tfidf_vocab_{suffix}.json")
            with open(p_vocab, "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "vocabulary": vocab,
                        "max_features": int(max_features),
                        "ngram_range": list(ngram_range),
                        "min_df": int(min_df),
                        "max_df": float(max_df),
                        "sublinear_tf": True,
                        "n_samples": int(len(texts)),
                        "n_features": int(X.shape[1]),
                    },
                    f,
                    ensure_ascii=False,
                )
            results[f"{prefix}_tfidf_vocab_{suffix}"] = p_vocab

    boss_df = read_jobs_from_mysql(cfg, tables=("job_info",), limit=limit)
    job51_df = read_jobs_from_mysql(cfg, tables=("job_info_51job",), limit=limit)
    all_df = pd.concat([boss_df, job51_df], ignore_index=True) if (not boss_df.empty or not job51_df.empty) else pd.DataFrame()

    build("boss", boss_df)
    build("51job", job51_df)
    build("all", all_df)

    return results


def clean_jobs_df(df: pd.DataFrame) -> pd.DataFrame:
    if df is None or df.empty:
        return pd.DataFrame()

    out = df.copy()
    for col in ["job_name", "company_name", "city", "experience", "education", "company_size", "company_industry"]:
        if col in out.columns:
            out[col] = out[col].astype(str).replace({"None": ""}).fillna("").str.strip()

    if "job_desc" in out.columns:
        out["job_desc"] = out["job_desc"].astype(str).replace({"None": ""}).fillna("")
    if "job_keywords" in out.columns:
        out["job_keywords"] = out["job_keywords"].astype(str).replace({"None": ""}).fillna("")

    if "job_url" in out.columns:
        out["job_url"] = out["job_url"].astype(str).replace({"None": ""}).fillna("").str.strip()

    if "salary_min" in out.columns:
        out["salary_min"] = pd.to_numeric(out["salary_min"], errors="coerce")
    if "salary_max" in out.columns:
        out["salary_max"] = pd.to_numeric(out["salary_max"], errors="coerce")
    if "salary_avg" in out.columns:
        out["salary_avg"] = pd.to_numeric(out["salary_avg"], errors="coerce")

    need_avg = out["salary_avg"].isna() if "salary_avg" in out.columns else pd.Series([True] * len(out))
    min_ok = out["salary_min"].notna() if "salary_min" in out.columns else pd.Series([False] * len(out))
    max_ok = out["salary_max"].notna() if "salary_max" in out.columns else pd.Series([False] * len(out))
    can_avg = need_avg & min_ok & max_ok
    if "salary_avg" in out.columns and can_avg.any():
        out.loc[can_avg, "salary_avg"] = (out.loc[can_avg, "salary_min"] + out.loc[can_avg, "salary_max"]) / 2.0

    if "salary_avg" in out.columns:
        out.loc[(out["salary_avg"] <= 0) | (out["salary_avg"] > 200), "salary_avg"] = pd.NA

    subset = []
    if "job_url" in out.columns:
        subset.append("job_url")
    subset += [c for c in ["job_name", "company_name", "city"] if c in out.columns]
    if subset:
        out = out.drop_duplicates(subset=subset, keep="first")

    if "job_desc" in out.columns:
        out = out[out["job_desc"].astype(str).str.strip().str.len() > 0]

    out = out.reset_index(drop=True)
    return out


_html_re = re.compile(r"<[^>]+>")
_space_re = re.compile(r"\s+")
_keep_re = re.compile(r"[^0-9A-Za-z\u4e00-\u9fff\+\#\.\-_/ ]+")
_ascii_token_re = re.compile(r"[A-Za-z][A-Za-z0-9\+\#\.\-_/]{1,}")
_num_token_re = re.compile(r"^\d+(?:\.\d+)?$")


def normalize_text(text: str) -> str:
    s = str(text or "")
    s = _html_re.sub(" ", s)
    s = s.replace("\u00a0", " ")

    s = s.replace("&nbsp;", " ")
    s = s.replace("&amp;", " ")
    s = s.replace("&lt;", " ")
    s = s.replace("&gt;", " ")
    s = s.replace("&quot;", " ")
    s = s.replace("&apos;", " ")

    s = re.sub(r"(?i)asp\.net", "aspnet", s)
    s = re.sub(r"(?i)\.net", "dotnet", s)

    s = _keep_re.sub(" ", s)
    s = _space_re.sub(" ", s).strip()
    return s


def _merge_phrases(tokens: List[str]) -> List[str]:
    if not tokens:
        return []
    out: List[str] = []
    i = 0
    while i < len(tokens):
        t1 = tokens[i]
        t2 = tokens[i + 1] if i + 1 < len(tokens) else ""
        t3 = tokens[i + 2] if i + 2 < len(tokens) else ""
        if t1 == "机器" and t2 == "学习":
            out.append("机器学习")
            i += 2
            continue
        if t1 == "深度" and t2 == "学习":
            out.append("深度学习")
            i += 2
            continue
        if t1 == "自然" and t2 == "语言" and t3 == "处理":
            out.append("自然语言处理")
            i += 3
            continue
        if t1 == "计算机" and t2 == "视觉":
            out.append("计算机视觉")
            i += 2
            continue
        if t1 == "大" and t2 == "数据":
            out.append("大数据")
            i += 2
            continue
        if t1 == "数据" and t2 in {"分析", "挖掘", "仓库", "治理", "开发", "处理", "科学"}:
            out.append("数据" + t2)
            i += 2
            continue
        out.append(t1)
        i += 1
    return out


def _is_core_token(t: str) -> bool:
    if not t:
        return False
    if _num_token_re.match(t):
        return False
    if t in {
        "开发",
        "技术",
        "熟悉",
        "具备",
        "系统",
        "设计",
        "项目",
        "优化",
        "团队",
        "数据",
    }:
        return False

    if _ascii_token_re.fullmatch(t):
        return True

    if any(ch in t for ch in ("+", "#", ".", "/", "_", "-")):
        return True

    if t.endswith(("框架", "平台", "协议", "数据库", "中间件", "算法", "模型", "工程", "架构", "集群", "服务")):
        return True

    if t in {
        "大数据",
        "数据分析",
        "数据挖掘",
        "数据仓库",
        "数据治理",
        "数据开发",
        "机器学习",
        "深度学习",
        "自然语言处理",
        "计算机视觉",
        "推荐系统",
        "知识图谱",
        "云原生",
        "微服务",
        "分布式",
        "高并发",
        "高可用",
        "容器",
        "爬虫",
        "可视化",
    }:
        return True

    if re.fullmatch(r"[\u4e00-\u9fff]{2,6}", t):
        return True

    return False


def build_stopwords(removed_terms: Iterable[str]) -> set:
    base = {
        "的",
        "了",
        "在",
        "是",
        "我",
        "有",
        "和",
        "就",
        "不",
        "人",
        "都",
        "一个",
        "也",
        "很",
        "到",
        "说",
        "要",
        "去",
        "你",
        "会",
        "着",
        "没有",
        "看",
        "好",
        "自己",
        "这",
        "以及",
        "等",
        "相关",
        "岗位",
        "职位",
        "工作",
        "职责",
        "职能",
        "要求",
        "能力",
        "经验",
        "以上",
        "以下",
        "优先",
        "负责",
        "参与",
        "协助",
        "配合",
        "完成",
        "实现",
        "推进",
        "推动",
        "落地",
        "支持",
        "跟进",
        "对接",
        "沟通",
        "协调",
        "优化",
        "迭代",
        "维护",
        "开发",
        "设计",
        "系统",
        "项目",
        "团队",
        "技术",
        "熟悉",
        "掌握",
        "精通",
        "了解",
        "具备",
        "良好",
        "优秀",
        "较强",
        "一定",
        "能够",
        "可以",
        "需要",
        "必须",
        "进行",
        "包括",
        "并",
        "及",
        "及其",
        "等同",
        "以上学历",
        "本科",
        "大专",
        "硕士",
        "博士",
        "应届",
        "毕业",
        "公司",
        "部门",
        "业务",
        "产品",
        "客户",
        "需求",
        "文档",
        "方案",
        "工具",
        "方法",
        "流程",
        "规范",
        "标准",
        "能力强",
        "经验丰富",
        "工程师",
        "and",
        "or",
        "to",
        "in",
        "with",
        "the",
        "of",
        "for",
        "on",
        "at",
        "by",
        "from",
        "as",
        "a",
        "an",
        "is",
        "are",
        "be",
        "been",
        "being",
        "this",
        "that",
        "these",
        "those",
        "it",
        "its",
        "we",
        "our",
        "us",
        "you",
        "your",
        "they",
        "their",
        "them",
        "can",
        "may",
        "will",
        "shall",
        "must",
        "should",
        "could",
        "would",
        "etc",
        "nbsp",
        "amp",
        "lt",
        "gt",
        "quot",
        "apos",
        "net",
        "代码",
        "分析",
        "挖掘",
        "仓库",
        "治理",
        "开发",
        "处理",
        "科学",
        "专业",
        "使用",
        "问题",
        "框架",
        "任职",
        "应用",
        "编写",
        "管理",
        "性能",
        "平台",
        "协作",
        "模型",
        "工程",
        "架构",
        "集群",
        "服务",
        "熟练",
        "独立",
        "解决",
        "岗位职责",
        "提升",
        "确保",
        "基础",
        "核心",
        "软件",
        "学习",
        "熟练掌握",
        "功能",
        "研发",
        "具有",
        "用户",
        "接口",
        "模块",
        "理解",
        "主流",
        "部署",
        "编程",
        "持续",
        "语言",
        "场景",
        "计算机"

    }
    for t in removed_terms or []:
        if t and len(t) <= 12:
            base.add(str(t).strip().lower())
    return base


def tokenize(
    text: str,
    stopwords: set,
    whitelist: Optional[set] = None,
    min_len: int = 2,
) -> List[str]:
    """分词与过滤。

    规则：
    - 先提取英文/符号 token（如 python、c++、k8s、dotnet），再走 jieba 分词
    - 过滤纯数字、长度过短、停用词
    - whitelist 不为 None 时，仅保留白名单内 token（用于技能口径）

    返回 token 列表（会进行少量短语合并，如 “机器 学习”→“机器学习”）。
    """

    s = normalize_text(text)
    tokens: List[str] = []

    for m in _ascii_token_re.findall(s):
        t = str(m or "").strip().lower()
        if not t:
            continue
        if len(t) < int(min_len):
            continue
        if t in stopwords:
            continue
        if whitelist is not None and t not in whitelist:
            continue
        tokens.append(t)

    for w in jieba.lcut(s):
        t = str(w or "").strip().lower()
        if not t:
            continue
        if len(t) < int(min_len):
            continue
        if _num_token_re.match(t):
            continue
        if t in stopwords:
            continue
        if whitelist is not None and t not in whitelist:
            continue
        tokens.append(t)

    return _merge_phrases(tokens)


def attach_tokens(
    df: pd.DataFrame,
    removed_terms_path: str,
    whitelist_path: Optional[str] = None,
    text_cols: Sequence[str] = ("job_name", "job_keywords", "job_desc"),
) -> pd.DataFrame:
    """为岗位数据附加文本字段与分词结果。

    生成字段：
    - text_raw：把 text_cols 拼接为原始文本
    - tokens/text：停用词过滤后的通用分词与空格拼接文本
    - tokens_skill/text_skill：若 whitelist 存在，按“技能白名单”过滤后的口径
    - tokens_core/text_core：用于“核心语义特征(Top Tokens)”的默认口径
      - 若 whitelist 存在：tokens_core = tokens_skill
      - 否则：对 tokens 做一次规则筛选（保留更像技能/领域词的 token）
    """

    removed_terms = load_removed_terms(removed_terms_path)
    stopwords = build_stopwords(removed_terms)
    whitelist = None
    if whitelist_path:
        wl = load_whitelist(whitelist_path)
        whitelist = set(wl) if wl else None

    out = df.copy()

    def join_text(row) -> str:
        parts = []
        for c in text_cols:
            if c in row and row[c] is not None:
                parts.append(str(row[c]))
        return " ".join(parts)

    out["text_raw"] = out.apply(join_text, axis=1)
    out["tokens"] = out["text_raw"].map(lambda s: tokenize(s, stopwords=stopwords, whitelist=None))
    out["text"] = out["tokens"].map(lambda xs: " ".join(xs))

    if whitelist is not None:
        out["tokens_skill"] = out["text_raw"].map(lambda s: tokenize(s, stopwords=stopwords, whitelist=whitelist))
        out["text_skill"] = out["tokens_skill"].map(lambda xs: " ".join(xs))
        out["tokens_core"] = out["tokens_skill"]
    else:
        out["tokens_core"] = out["tokens"].map(lambda xs: [t for t in (xs or []) if _is_core_token(str(t or "").strip().lower())])

    out["text_core"] = out["tokens_core"].map(lambda xs: " ".join(xs))
    return out


def reduce_dataframe(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()

    if "salary_avg" in out.columns:
        out["salary_avg"] = pd.to_numeric(out["salary_avg"], errors="coerce")

    keep_cols = [
        c
        for c in [
            "id",
            "source_table",
            "job_name",
            "company_name",
            "city",
            "job_url",
            "salary_min",
            "salary_max",
            "salary_avg",
            "experience",
            "education",
            "company_size",
            "company_industry",
            "publish_date",
            "created_at",
            "text",
            "text_skill",
            "text_core",
        ]
        if c in out.columns
    ]
    out = out[keep_cols].copy()
    out = out.dropna(subset=["text"])
    out = out[out["text"].astype(str).str.len() > 0]
    out = out.reset_index(drop=True)
    return out


def export_stats(df: pd.DataFrame, out_dir: str) -> Dict[str, str]:
    """导出统计类产物（主要用于前端图表/Top Tokens）。

    输出文件示例：
    - salary_by_city.csv：按城市统计薪资
    - value_counts_*.csv：若干字段的频次统计
    - token_count_desc.csv：每条文本 token 数描述统计
    - top_tokens.csv：核心语义特征词频 Top200

    Top Tokens 使用口径优先级：text_core > text_skill > text。
    """

    os.makedirs(out_dir, exist_ok=True)
    paths = {}

    base_cols = [c for c in ["city", "education", "experience", "company_industry"] if c in df.columns]
    if "salary_avg" in df.columns:
        salary_city = (
            df.dropna(subset=["salary_avg"])
            .groupby("city", as_index=False)["salary_avg"]
            .agg(["count", "mean", "median", "min", "max"])
        )
        salary_city.columns = ["city", "count", "mean", "median", "min", "max"]
        p = os.path.join(out_dir, "salary_by_city.csv")
        salary_city.to_csv(p, index=False, encoding="utf-8-sig")
        paths["salary_by_city"] = p

    for col in base_cols:
        vc = df[col].astype(str).replace({"": pd.NA}).dropna().value_counts().reset_index()
        vc.columns = [col, "count"]
        p = os.path.join(out_dir, f"value_counts_{col}.csv")
        vc.to_csv(p, index=False, encoding="utf-8-sig")
        paths[f"value_counts_{col}"] = p

    text_col = "text_core" if "text_core" in df.columns else ("text_skill" if "text_skill" in df.columns else "text")
    if text_col in df.columns:
        token_counts = df[text_col].astype(str).str.split().map(len)
        token_desc = token_counts.describe().to_frame(name="token_count")
        p = os.path.join(out_dir, "token_count_desc.csv")
        token_desc.to_csv(p, encoding="utf-8-sig")
        paths["token_count_desc"] = p

        all_tokens = df[text_col].astype(str).str.split()
        freq = pd.Series([t for xs in all_tokens for t in xs]).value_counts().head(200).reset_index()
        freq.columns = ["token", "count"]
        p = os.path.join(out_dir, "top_tokens.csv")
        freq.to_csv(p, index=False, encoding="utf-8-sig")
        paths["top_tokens"] = p

    return paths


def _require_sklearn():
    try:
        import sklearn  # noqa: F401
    except Exception as e:
        raise RuntimeError("缺少依赖: scikit-learn。请先在 crawler 目录执行: pip install -r requirements.txt") from e


def _require_matplotlib():
    try:
        import matplotlib
    except Exception as e:
        raise RuntimeError("缺少依赖: matplotlib。请先在 crawler 目录执行: pip install -r requirements.txt") from e
    try:
        matplotlib.use("Agg", force=True)
    except Exception:
        return
    try:
        import matplotlib.pyplot as plt

        plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "Arial Unicode MS", "DejaVu Sans"]
        plt.rcParams["axes.unicode_minus"] = False
    except Exception:
        return


def run_cluster(
    df: pd.DataFrame,
    out_dir: str,
    n_clusters: int = 8,
    max_features: int = 8000,
    random_state: int = 42,
) -> Dict[str, str]:
    _require_sklearn()
    _require_matplotlib()

    from sklearn.cluster import MiniBatchKMeans
    from sklearn.decomposition import PCA, TruncatedSVD
    from sklearn.feature_extraction.text import TfidfVectorizer

    import matplotlib.pyplot as plt

    os.makedirs(out_dir, exist_ok=True)
    df2 = df.copy()
    text_col = "text_skill" if "text_skill" in df2.columns else "text"
    df2 = df2.dropna(subset=[text_col])
    df2 = df2[df2[text_col].astype(str).str.len() > 0].reset_index(drop=True)

    vectorizer = TfidfVectorizer(
        analyzer="word",
        token_pattern=r"(?u)\b\w+\b",
        lowercase=False,
        max_features=int(max_features),
    )
    X = vectorizer.fit_transform(df2[text_col].astype(str).tolist())

    if X.shape[1] > 3000:
        reducer = TruncatedSVD(n_components=2, random_state=int(random_state))
        Z = reducer.fit_transform(X)
        reduce_name = "svd2"
    else:
        dense = X.toarray()
        reducer = PCA(n_components=2, random_state=int(random_state))
        Z = reducer.fit_transform(dense)
        reduce_name = "pca2"

    km = MiniBatchKMeans(n_clusters=int(n_clusters), random_state=int(random_state), batch_size=2048, n_init=10)
    labels = km.fit_predict(X)

    df2["cluster_id"] = labels.astype(int)
    df2["dim1"] = Z[:, 0]
    df2["dim2"] = Z[:, 1]

    feature_names = vectorizer.get_feature_names_out()
    centers = km.cluster_centers_
    cluster_topics: Dict[int, List[str]] = {}
    for cid in range(int(n_clusters)):
        if cid >= centers.shape[0]:
            cluster_topics[cid] = []
            continue
        row = centers[cid]
        top_idx = row.argsort()[::-1][:5]
        cluster_topics[cid] = [str(feature_names[i]) for i in top_idx if i < len(feature_names)]

    paths = {}
    p_features = os.path.join(out_dir, "cluster_features.csv")
    df2.to_csv(p_features, index=False, encoding="utf-8-sig")
    paths["cluster_features"] = p_features

    p_vocab = os.path.join(out_dir, "tfidf_vocab.json")
    with open(p_vocab, "w", encoding="utf-8") as f:
        json.dump({"vocabulary_size": int(len(vectorizer.vocabulary_)), "max_features": int(max_features)}, f, ensure_ascii=False, indent=2)
    paths["tfidf_vocab"] = p_vocab

    cmap_name = "tab10" if int(n_clusters) <= 10 else "tab20"
    cmap = plt.get_cmap(cmap_name)

    fig = plt.figure(figsize=(12, 7))
    ax = fig.add_subplot(111)
    for cid in range(int(n_clusters)):
        sub = df2[df2["cluster_id"] == cid]
        if sub.empty:
            continue
        color = cmap(cid % cmap.N)
        topic = cluster_topics.get(cid) or []
        topic_text = "/".join(topic[:3]) if topic else "（无关键词）"
        label = f"主题{cid + 1}: {topic_text}"
        ax.scatter(sub["dim1"], sub["dim2"], s=10, color=color, alpha=0.78, label=label)

        cx = float(sub["dim1"].mean())
        cy = float(sub["dim2"].mean())
        ax.text(cx, cy, f"主题{cid + 1}", fontsize=9, weight="bold", color=color, ha="center", va="center")

    ax.set_title(f"招聘岗位文本主题聚类（{reduce_name}+KMeans，K={n_clusters}）")
    ax.set_xlabel("降维维度1")
    ax.set_ylabel("降维维度2")
    ax.grid(True, linestyle="--", linewidth=0.6, alpha=0.25)

    leg = ax.legend(
        title="颜色=主题簇（显示Top关键词）",
        loc="center left",
        bbox_to_anchor=(1.02, 0.5),
        frameon=True,
        fontsize=9,
        title_fontsize=10,
    )
    try:
        leg.get_frame().set_alpha(0.92)
        leg.get_frame().set_linewidth(0.8)
    except Exception:
        pass

    p_png = os.path.join(out_dir, "cluster_scatter.png")
    fig.tight_layout(rect=[0, 0, 0.80, 1])
    fig.savefig(p_png, dpi=180)
    plt.close(fig)
    paths["cluster_scatter_png"] = p_png

    return paths


def make_salary_bins(df: pd.DataFrame, n_bins: int = 5) -> pd.Series:
    import numpy as np

    s = pd.to_numeric(df.get("salary_avg"), errors="coerce")
    if s is None:
        return pd.Series([np.nan] * len(df), index=df.index, dtype="float64")
    s2 = s.dropna()
    if s2.empty:
        return pd.Series([np.nan] * len(df), index=df.index, dtype="float64")
    try:
        bins = pd.qcut(s2, q=int(n_bins), labels=False, duplicates="drop")
    except Exception:
        return pd.Series([np.nan] * len(df), index=df.index, dtype="float64")
    if bins.nunique(dropna=True) <= 1:
        return pd.Series([0.0 if pd.notna(v) else np.nan for v in s], index=df.index, dtype="float64")
    uniq = sorted([int(x) for x in pd.Series(bins).dropna().unique()])
    remap = {old: new for new, old in enumerate(uniq)}
    out = pd.Series([np.nan] * len(df), index=df.index, dtype="float64")
    out.loc[s2.index] = pd.Series(bins).map(lambda x: float(remap.get(int(x))) if pd.notna(x) else np.nan).astype("float64")
    return out


def _require_torch():
    try:
        import torch  # noqa: F401
    except Exception as e:
        raise RuntimeError("缺少依赖: torch。请先在 crawler 目录执行: pip install -r requirements.txt") from e


def train_mlp_salary_level(
    df: pd.DataFrame,
    out_dir: str,
    max_features: int = 20000,
    hidden: int = 256,
    batch_size: int = 128,
    epochs: int = 3,
    lr: float = 1e-3,
    random_state: int = 42,
) -> Dict[str, str]:
    _require_sklearn()
    _require_torch()

    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.model_selection import train_test_split
    from sklearn.preprocessing import OneHotEncoder

    import numpy as np
    import torch
    from torch import nn
    from torch.utils.data import DataLoader, TensorDataset
    from scipy import sparse

    os.makedirs(out_dir, exist_ok=True)
    df2 = df.copy()
    df2["salary_level"] = make_salary_bins(df2, n_bins=5)
    df2 = df2.dropna(subset=["text", "salary_level"]).reset_index(drop=True)
    if df2.empty:
        raise RuntimeError("可用于训练的数据为空：需要 text 与 salary_avg（用于分箱）。")

    y = df2["salary_level"].astype(int).values
    meta_cols = [c for c in ["city", "education", "experience", "company_industry", "company_size"] if c in df2.columns]

    idx = np.arange(len(df2))
    train_idx, val_idx = train_test_split(
        idx,
        test_size=0.2,
        random_state=int(random_state),
        stratify=y if len(set(y)) > 1 else None,
    )

    train_texts = df2.loc[train_idx, "text"].astype(str).tolist()
    val_texts = df2.loc[val_idx, "text"].astype(str).tolist()
    y_train = y[train_idx]
    y_val = y[val_idx]

    vectorizer = TfidfVectorizer(
        analyzer="word",
        token_pattern=r"(?u)\b\w+\b",
        lowercase=False,
        max_features=int(max_features),
        ngram_range=(1, 2),
        min_df=2,
        max_df=0.98,
        sublinear_tf=True,
    )
    X_text_train = vectorizer.fit_transform(train_texts)
    X_text_val = vectorizer.transform(val_texts)

    meta_encoder = None
    X_meta_train = None
    X_meta_val = None
    if meta_cols:
        meta_train_df = df2.loc[train_idx, meta_cols].fillna("").astype(str)
        meta_val_df = df2.loc[val_idx, meta_cols].fillna("").astype(str)
        try:
            meta_encoder = OneHotEncoder(handle_unknown="ignore", sparse_output=True)
        except TypeError:
            meta_encoder = OneHotEncoder(handle_unknown="ignore", sparse=True)
        X_meta_train = meta_encoder.fit_transform(meta_train_df)
        X_meta_val = meta_encoder.transform(meta_val_df)

    if X_meta_train is not None:
        X_train = sparse.hstack([X_text_train, X_meta_train], format="csr")
        X_val = sparse.hstack([X_text_val, X_meta_val], format="csr")
    else:
        X_train = X_text_train.tocsr()
        X_val = X_text_val.tocsr()

    X_train_t = torch.from_numpy(X_train.toarray().astype(np.float32))
    X_val_t = torch.from_numpy(X_val.toarray().astype(np.float32))
    y_train_t = torch.from_numpy(y_train.astype(np.int64))
    y_val_t = torch.from_numpy(y_val.astype(np.int64))

    train_loader = DataLoader(TensorDataset(X_train_t, y_train_t), batch_size=int(batch_size), shuffle=True)
    val_loader = DataLoader(TensorDataset(X_val_t, y_val_t), batch_size=int(batch_size), shuffle=False)

    input_dim = int(X_train_t.shape[1])
    num_classes = int(max(y_train.max(), y_val.max()) + 1)

    model = nn.Sequential(
        nn.Linear(input_dim, int(hidden)),
        nn.BatchNorm1d(int(hidden)),
        nn.ReLU(),
        nn.Dropout(0.35),
        nn.Linear(int(hidden), int(hidden // 2)),
        nn.BatchNorm1d(int(hidden // 2)),
        nn.ReLU(),
        nn.Dropout(0.35),
        nn.Linear(int(hidden // 2), num_classes),
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)

    counts = np.bincount(y_train.astype(int), minlength=int(num_classes)).astype(np.float64)
    weights = counts.sum() / (counts + 1e-6)
    weights = weights / max(weights.mean(), 1e-6)
    loss_fn = nn.CrossEntropyLoss(weight=torch.tensor(weights, dtype=torch.float32).to(device))
    optim = torch.optim.AdamW(model.parameters(), lr=float(lr), weight_decay=1e-4)

    def eval_acc(loader) -> float:
        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for xb, yb in loader:
                xb = xb.to(device)
                yb = yb.to(device)
                logits = model(xb)
                pred = torch.argmax(logits, dim=1)
                correct += int((pred == yb).sum().item())
                total += int(yb.numel())
        return float(correct / max(total, 1))

    history = []
    best_state = None
    best_val = -1.0
    for epoch in range(int(epochs)):
        model.train()
        for xb, yb in train_loader:
            xb = xb.to(device)
            yb = yb.to(device)
            optim.zero_grad()
            logits = model(xb)
            loss = loss_fn(logits, yb)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optim.step()
        train_acc = eval_acc(train_loader)
        val_acc = eval_acc(val_loader)
        history.append({"epoch": epoch + 1, "train_acc": train_acc, "val_acc": val_acc})
        if val_acc > best_val:
            best_val = val_acc
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    model_path = os.path.join(out_dir, "mlp_salary_level.pt")
    to_save_state = best_state if best_state is not None else model.state_dict()
    torch.save({"model_state": to_save_state, "input_dim": input_dim, "num_classes": num_classes}, model_path)

    vec_path = os.path.join(out_dir, "mlp_tfidf_vectorizer.json")
    vocab = {str(k): int(v) for k, v in (vectorizer.vocabulary_ or {}).items()}
    with open(vec_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "vocabulary": vocab,
                "max_features": int(max_features),
                "ngram_range": [1, 2],
                "min_df": 2,
                "max_df": 0.98,
                "sublinear_tf": True,
                "meta_cols": meta_cols,
            },
            f,
            ensure_ascii=False,
        )

    meta_path = os.path.join(out_dir, "mlp_meta_encoder.json")
    if meta_encoder is not None and meta_cols:
        cats = {}
        for col, cat in zip(meta_cols, meta_encoder.categories_):
            cats[str(col)] = [str(x) for x in list(cat)]
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump({"meta_cols": meta_cols, "categories": cats}, f, ensure_ascii=False)
    else:
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump({"meta_cols": meta_cols, "categories": {}}, f, ensure_ascii=False)

    hist_path = os.path.join(out_dir, "mlp_history.csv")
    pd.DataFrame(history).to_csv(hist_path, index=False, encoding="utf-8-sig")

    return {"mlp_model": model_path, "mlp_vectorizer": vec_path, "mlp_meta_encoder": meta_path, "mlp_history": hist_path}


def train_textcnn_salary_level(
    df: pd.DataFrame,
    out_dir: str,
    vocab_size: int = 30000,
    max_len: int = 200,
    embed_dim: int = 128,
    num_filters: int = 128,
    batch_size: int = 128,
    epochs: int = 3,
    lr: float = 1e-3,
    random_state: int = 42,
) -> Dict[str, str]:
    _require_sklearn()
    _require_torch()

    from sklearn.model_selection import train_test_split

    import numpy as np
    import torch
    from torch import nn
    from torch.utils.data import DataLoader, TensorDataset

    os.makedirs(out_dir, exist_ok=True)
    df2 = df.copy()
    df2["salary_level"] = make_salary_bins(df2, n_bins=5)
    df2 = df2.dropna(subset=["tokens", "salary_level"]).reset_index(drop=True)
    if df2.empty:
        raise RuntimeError("可用于训练的数据为空：需要 tokens 与 salary_avg（用于分箱）。")

    y = df2["salary_level"].astype(int).values
    tokens_list = df2["tokens"].tolist()
    meta_cols = [c for c in ["city", "education", "experience", "company_industry", "company_size"] if c in df2.columns]

    idx = np.arange(len(df2))
    train_idx, val_idx = train_test_split(
        idx,
        test_size=0.2,
        random_state=int(random_state),
        stratify=y if len(set(y)) > 1 else None,
    )
    y_train = y[train_idx]
    y_val = y[val_idx]
    train_tokens = [tokens_list[i] for i in train_idx]
    val_tokens = [tokens_list[i] for i in val_idx]

    freq = {}
    for xs in train_tokens:
        for t in xs:
            freq[t] = freq.get(t, 0) + 1
    vocab_items = sorted(freq.items(), key=lambda x: (-x[1], x[0]))
    vocab_items = vocab_items[: max(1, int(vocab_size) - 2)]
    word2id = {w: i + 2 for i, (w, _) in enumerate(vocab_items)}
    word2id["<pad>"] = 0
    word2id["<unk>"] = 1

    def encode(xs: List[str]) -> List[int]:
        ids = [word2id.get(t, 1) for t in xs[: int(max_len)]]
        if len(ids) < int(max_len):
            ids += [0] * (int(max_len) - len(ids))
        return ids

    X_train = np.asarray([encode(xs) for xs in train_tokens], dtype=np.int64)
    X_val = np.asarray([encode(xs) for xs in val_tokens], dtype=np.int64)

    def build_meta_maps(df_part: pd.DataFrame, cols: List[str]) -> Dict[str, Dict[str, int]]:
        maps: Dict[str, Dict[str, int]] = {}
        for c in cols:
            vals = df_part[c].fillna("").astype(str).map(lambda x: x.strip()).tolist()
            uniq = sorted([v for v in set(vals) if v])
            m = {v: i + 1 for i, v in enumerate(uniq)}
            maps[str(c)] = m
        return maps

    meta_maps = build_meta_maps(df2.loc[train_idx], meta_cols) if meta_cols else {}

    def encode_meta(df_part: pd.DataFrame) -> np.ndarray:
        if not meta_cols:
            return np.zeros((len(df_part), 0), dtype=np.int64)
        arr = np.zeros((len(df_part), len(meta_cols)), dtype=np.int64)
        for j, c in enumerate(meta_cols):
            m = meta_maps.get(str(c), {})
            vals = df_part[c].fillna("").astype(str).map(lambda x: x.strip()).tolist()
            arr[:, j] = [int(m.get(v, 0)) for v in vals]
        return arr

    meta_train = encode_meta(df2.loc[train_idx])
    meta_val = encode_meta(df2.loc[val_idx])

    X_train_t = torch.from_numpy(X_train)
    X_val_t = torch.from_numpy(X_val)
    meta_train_t = torch.from_numpy(meta_train)
    meta_val_t = torch.from_numpy(meta_val)
    y_train_t = torch.from_numpy(y_train.astype(np.int64))
    y_val_t = torch.from_numpy(y_val.astype(np.int64))

    train_loader = DataLoader(TensorDataset(X_train_t, meta_train_t, y_train_t), batch_size=int(batch_size), shuffle=True)
    val_loader = DataLoader(TensorDataset(X_val_t, meta_val_t, y_val_t), batch_size=int(batch_size), shuffle=False)

    num_classes = int(max(y_train.max(), y_val.max()) + 1)
    vsize = int(max(word2id.values()) + 1)

    class TextCNNWithMeta(nn.Module):
        def __init__(self):
            super().__init__()
            self.emb = nn.Embedding(vsize, int(embed_dim), padding_idx=0)
            self.emb_dropout = nn.Dropout(0.1)
            self.convs = nn.ModuleList(
                [
                    nn.Conv1d(int(embed_dim), int(num_filters), kernel_size=3, padding=1),
                    nn.Conv1d(int(embed_dim), int(num_filters), kernel_size=4, padding=2),
                    nn.Conv1d(int(embed_dim), int(num_filters), kernel_size=5, padding=2),
                ]
            )
            self.dropout = nn.Dropout(0.25)
            self.meta_cols = meta_cols
            self.meta_embs = nn.ModuleList()
            meta_dim = 8
            for c in meta_cols:
                size = int(max(meta_maps.get(str(c), {}).values(), default=0) + 1)
                self.meta_embs.append(nn.Embedding(max(size, 1), meta_dim))
            self.fc = nn.Linear(int(num_filters) * len(self.convs) + meta_dim * len(meta_cols), num_classes)

        def forward(self, x, meta):
            emb = self.emb(x)
            emb = self.emb_dropout(emb)
            emb = emb.transpose(1, 2)
            feats = []
            for conv in self.convs:
                h = torch.relu(conv(emb))
                h = torch.max(h, dim=2).values
                feats.append(h)
            z = torch.cat(feats, dim=1)
            if len(self.meta_embs) > 0 and meta is not None and meta.numel() > 0:
                meta_vecs = []
                for i, emb_layer in enumerate(self.meta_embs):
                    meta_vecs.append(emb_layer(meta[:, i]))
                meta_vec = torch.cat(meta_vecs, dim=1)
                z = torch.cat([z, meta_vec], dim=1)
            z = self.dropout(z)
            return self.fc(z)

    model = TextCNNWithMeta()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device)

    counts = np.bincount(y_train.astype(int), minlength=int(num_classes)).astype(np.float64)
    weights = counts.sum() / (counts + 1e-6)
    weights = weights / max(weights.mean(), 1e-6)
    loss_fn = nn.CrossEntropyLoss(weight=torch.tensor(weights, dtype=torch.float32).to(device))
    optim = torch.optim.AdamW(model.parameters(), lr=float(lr), weight_decay=1e-4)

    def eval_acc(loader) -> float:
        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for xb, mb, yb in loader:
                xb = xb.to(device)
                mb = mb.to(device)
                yb = yb.to(device)
                logits = model(xb, mb)
                pred = torch.argmax(logits, dim=1)
                correct += int((pred == yb).sum().item())
                total += int(yb.numel())
        return float(correct / max(total, 1))

    history = []
    for epoch in range(int(epochs)):
        model.train()
        for xb, mb, yb in train_loader:
            xb = xb.to(device)
            mb = mb.to(device)
            yb = yb.to(device)
            optim.zero_grad()
            logits = model(xb, mb)
            loss = loss_fn(logits, yb)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optim.step()
        train_acc = eval_acc(train_loader)
        val_acc = eval_acc(val_loader)
        history.append({"epoch": epoch + 1, "train_acc": train_acc, "val_acc": val_acc})

    model_path = os.path.join(out_dir, "textcnn_salary_level.pt")
    torch.save(
        {
            "model_state": model.state_dict(),
            "word2id": word2id,
            "max_len": int(max_len),
            "num_classes": num_classes,
            "meta_cols": meta_cols,
            "meta_maps": meta_maps,
        },
        model_path,
    )

    hist_path = os.path.join(out_dir, "textcnn_history.csv")
    pd.DataFrame(history).to_csv(hist_path, index=False, encoding="utf-8-sig")

    vocab_path = os.path.join(out_dir, "textcnn_vocab.json")
    with open(vocab_path, "w", encoding="utf-8") as f:
        json.dump(
            {"word2id": word2id, "vocab_size": int(vsize), "meta_cols": meta_cols, "meta_maps": meta_maps},
            f,
            ensure_ascii=False,
        )

    return {"textcnn_model": model_path, "textcnn_vocab": vocab_path, "textcnn_history": hist_path}


def visualize_basic(df: pd.DataFrame, out_dir: str) -> Dict[str, str]:
    _require_matplotlib()
    import matplotlib.pyplot as plt

    os.makedirs(out_dir, exist_ok=True)
    paths = {}

    if "salary_avg" in df.columns:
        s = pd.to_numeric(df["salary_avg"], errors="coerce").dropna()
        if not s.empty:
            fig = plt.figure(figsize=(10, 6))
            ax = fig.add_subplot(111)
            ax.hist(s.values, bins=40, color="#4C78A8", alpha=0.85)
            ax.set_title("Salary Avg Distribution (k RMB/month)")
            ax.set_xlabel("salary_avg")
            ax.set_ylabel("count")
            fig.tight_layout()
            p = os.path.join(out_dir, "salary_hist.png")
            fig.savefig(p, dpi=180)
            plt.close(fig)
            paths["salary_hist_png"] = p

    if "city" in df.columns:
        vc = df["city"].astype(str).replace({"": pd.NA}).dropna().value_counts().head(20)
        if not vc.empty:
            fig = plt.figure(figsize=(10, 7))
            ax = fig.add_subplot(111)
            ax.barh(vc.index[::-1], vc.values[::-1], color="#F58518", alpha=0.9)
            ax.set_title("Top Cities (count)")
            ax.set_xlabel("count")
            fig.tight_layout()
            p = os.path.join(out_dir, "top_cities.png")
            fig.savefig(p, dpi=180)
            plt.close(fig)
            paths["top_cities_png"] = p

    return paths


def build_arg_parser() -> argparse.ArgumentParser:
    """命令行参数。

    cmd 说明：
    - preprocess：只做读库/清洗/分词/导出 jobs_clean/jobs_reduced
    - export-clean：同 preprocess，并额外覆盖写 raw_dir 的 *_clean/_clean_nlp
    - export-features：导出 TF-IDF 稀疏矩阵与词表（boss/51job/all）
    - stats：导出统计报表（包含 Top Tokens）
    - cluster：TF-IDF + 降维 + KMeans 聚类，并输出散点图
    - train-mlp / train-textcnn：训练薪资分档模型（依赖 torch）
    - viz：输出基础可视化图片
    - dashboard：一次性跑 stats/cluster/viz/train-mlp/train-textcnn
    """

    p = argparse.ArgumentParser(prog="nlp_job_pipeline.py")
    p.add_argument("--db-host", default=os.environ.get("JOBDATA_DB_HOST", "localhost"))
    p.add_argument("--db-port", type=int, default=int(os.environ.get("JOBDATA_DB_PORT", "3306")))
    p.add_argument("--db-user", default=os.environ.get("JOBDATA_DB_USER", "root"))
    p.add_argument("--db-password", default=os.environ.get("JOBDATA_DB_PASSWORD", "123456ppoo"))
    p.add_argument("--db-name", default=os.environ.get("JOBDATA_DB_NAME", "job_data"))
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--output-dir", default=_default_output_dir())
    p.add_argument("--raw-dir", default=_default_raw_dir())
    p.add_argument("--text-features-dir", default=_default_text_features_dir())
    p.add_argument("--removed-terms", default=os.path.join(_project_root(), "data", "text_features", "removed_terms.txt"))
    p.add_argument("--whitelist", default=os.path.join(_project_root(), "data", "text_features", "skill_whitelist.txt"))
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("preprocess")
    sub.add_parser("export-clean")
    f = sub.add_parser("export-features")
    f.add_argument("--max-features", type=int, default=20000)
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

    sub.add_parser("viz")
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
    print("__PIPELINE_JSON__" + json.dumps(payload, ensure_ascii=False))


def main():
    args = build_arg_parser().parse_args()
    cfg = DbConfig(
        host=args.db_host,
        port=args.db_port,
        user=args.db_user,
        password=args.db_password,
        database=args.db_name,
    )

    limit = int(args.limit) if int(args.limit) > 0 else None
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

    base_artifacts = {
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

    if args.cmd == "preprocess":
        _emit_pipeline_json(base_artifacts)
        return

    if args.cmd == "export-clean":
        _emit_pipeline_json(base_artifacts)
        return
    
    if args.cmd == "export-features":
        try:
            feat_paths = export_text_features(
                cfg,
                text_features_dir=text_features_dir,
                removed_terms_path=args.removed_terms,
                whitelist_path=args.whitelist,
                limit=limit,
                max_features=int(getattr(args, "max_features", 20000)),
            )
            base_artifacts["artifacts"].update(feat_paths)
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("export-features 失败: " + str(e))
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

    if args.cmd == "viz":
        paths = visualize_basic(reduced, run_dir)
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
            base_artifacts["artifacts"].update(visualize_basic(reduced, run_dir))
        except Exception as e:
            base_artifacts.setdefault("errors", []).append("viz 失败: " + str(e))

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
