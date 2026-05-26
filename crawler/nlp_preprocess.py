"""招聘数据 NLP：数据预处理模块。"""

import os
import re
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Sequence

import jieba
import pandas as pd
import psycopg2


@dataclass(frozen=True)
class DbConfig:
    """数据库连接配置，不可变数据类，存储 MySQL 连接所需的各项参数。"""
    host: str = ""
    port: int = 0
    user: str = ""
    password: str = ""
    database: str = ""
    charset: str = "utf8mb4"

    def to_pymysql_kwargs(self) -> Dict[str, object]:
        """将配置转为数据库连接库可接受的关键字参数字典（当前用于 psycopg2.connect），缺失必填字段时抛出 RuntimeError。"""
        if not str(self.host or "").strip():
            raise RuntimeError("缺少数据库配置: host")
        if not int(self.port or 0):
            raise RuntimeError("缺少数据库配置: port")
        if not str(self.user or "").strip():
            raise RuntimeError("缺少数据库配置: user")
        if not str(self.password or "").strip():
            raise RuntimeError("缺少数据库配置: password")
        if not str(self.database or "").strip():
            raise RuntimeError("缺少数据库配置: database")
        return {
            "host": self.host,
            "port": int(self.port),
            "user": self.user,
            "password": self.password,
            "dbname": self.database,
        }


def connect_mysql(cfg: DbConfig):
    """使用 DbConfig 建立数据库连接并返回连接对象。"""
    return psycopg2.connect(**cfg.to_pymysql_kwargs())


def read_jobs_from_mysql(
    cfg: DbConfig,
    tables: Sequence[str] = ("job_info", "job_info_51job"),
    limit: Optional[int] = None,
) -> pd.DataFrame:
    """从 MySQL 的指定表中读取招聘数据，返回合并后的 DataFrame。支持限制读取条数用于调试。"""
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
    return pd.concat(frames, ignore_index=True)


def _read_lines(path: str) -> List[str]:
    """读取文本文件的所有行，去除首尾空白，返回行列表。文件不存在时返回空列表。"""
    if not path or not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        return [line.strip() for line in f.readlines()]


def load_removed_terms(removed_terms_path: str) -> List[str]:
    """从停用词/移除词配置文件中加载需要过滤的关键词列表，跳过标题行和注释行。"""
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
    """从技能白名单文件中加载允许保留的关键词列表，每行一个词，自动转小写。"""
    items = []
    for line in _read_lines(whitelist_path):
        t = str(line or "").strip().lower()
        if t:
            items.append(t)
    return items


def clean_jobs_df(df: pd.DataFrame) -> pd.DataFrame:
    """清洗原始招聘 DataFrame：处理缺失值、补齐薪资均值、去重并过滤空描述行。"""
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
    """对原始文本进行标准化处理：去除 HTML 标签、转义字符、特殊符号，统一空白字符。"""
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
    """将分词后相邻的多个 token 合并为复合短语（如"机器"+"学习"→"机器学习"、"自然"+"语言"+"处理"→"自然语言处理"等）。"""
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
    """判断一个 token 是否属于核心技能词汇（排除数字 token、通用动词/名词、软技能词，保留专业技术术语）。"""
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
    """构建停用词集合：内置大量中英文通用停用词，并合并自定义移除词列表。"""
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
        "计算机",
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
    """对单条文本进行分词：先提取英文技术词汇，再用 jieba 切中文，过滤停用词与白名单，最后调用短语合并。"""
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
    """对 DataFrame 中的文本列进行分词：拼接指定列文本，加载停用词/白名单，生成 tokens/text/text_skill/tokens_core 等列。"""
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
    """精简 DataFrame，仅保留 NLP 相关列（id/job_name/salary/text/text_core 等），并过滤空文本行。"""
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


def export_cleaned_to_raw_dir(
    cfg: DbConfig,
    raw_dir: str,
    removed_terms_path: str,
    whitelist_path: Optional[str],
    limit: Optional[int] = None,
) -> Dict[str, str]:
    """从 MySQL 读取 BOSS/51job 数据，清洗并分词后导出为 CSV 到 raw_dir，返回文件路径字典。"""
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
