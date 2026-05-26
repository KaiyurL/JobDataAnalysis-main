"""招聘数据 NLP：数据可视化与统计模块。"""

import json
import os
import re
from typing import Dict, List, Optional, Tuple

import pandas as pd


def export_stats(df: pd.DataFrame, out_dir: str) -> Dict[str, str]:
    """导出 Top 200 高频 token 统计为 CSV 文件，返回产物路径字典。"""
    os.makedirs(out_dir, exist_ok=True)
    paths = {}

    text_col = "text_core" if "text_core" in df.columns else ("text_skill" if "text_skill" in df.columns else "text")
    if text_col in df.columns:
        all_tokens = df[text_col].astype(str).str.split()
        freq = pd.Series([t for xs in all_tokens for t in xs]).value_counts().head(200).reset_index()
        freq.columns = ["token", "count"]
        p = os.path.join(out_dir, "top_tokens.csv")
        freq.to_csv(p, index=False, encoding="utf-8-sig")
        paths["top_tokens"] = p

    return paths



def export_job_skill_heatmap(
    df: pd.DataFrame,
    out_dir: str,
    top_roles: int = 15,
    top_skills: int = 20,
    token_col_candidates: Tuple[str, ...] = ("tokens_skill", "tokens_core", "tokens"),
) -> Dict[str, str]:
    """生成岗位-技能热力图数据 JSON。
    按 top_roles 个岗位与 top_skills 个核心技能计算共现频次矩阵，
    过滤软技能/泛化词，仅保留硬技术关键词，输出供前端 ECharts Heatmap 渲染的数据。
    返回产物路径字典。"""
    import numpy as np

    os.makedirs(out_dir, exist_ok=True)

    if df is None or df.empty:
        return {}
    if "job_name" not in df.columns:
        return {}

    token_col: Optional[str] = None
    for c in token_col_candidates:
        if c in df.columns:
            token_col = c
            break
    if token_col is None:
        return {}

    def norm_role(s: str) -> str:
        t = str(s or "").strip()
        if not t:
            return ""
        t = re.sub(r"[\[\]（）()【】]", " ", t)
        t = re.sub(r"\s+", " ", t).strip()
        if len(t) > 18:
            t = t[:18]
        return t

    tmp = df.copy()
    tmp["role"] = tmp["job_name"].map(norm_role)
    tmp = tmp[tmp["role"].astype(str).str.len() > 0].copy()
    if tmp.empty:
        return {}

    role_counts = tmp["role"].value_counts().head(int(top_roles))
    roles: List[str] = role_counts.index.tolist()
    tmp = tmp[tmp["role"].isin(roles)].copy()
    if tmp.empty:
        return {}

    deny = {
        "精神",
        "责任心",
        "体验",
        "质量",
        "扎实",
        "认真",
        "细心",
        "负责",
        "积极",
        "主动",
        "热情",
        "敬业",
        "踏实",
        "勤奋",
        "耐心",
        "沟通",
        "表达",
        "协作",
        "团队",
        "抗压",
        "执行力",
        "学习",
        "学习能力",
        "逻辑",
        "思维",
        "态度",
        "意识",
        "能力",
        "基础",
        "良好",
        "优秀",
        "较强",
        "较好",
        "熟悉",
        "掌握",
        "精通",
        "了解",
        "具备",
    }

    allow_cn = {
        "前端",
        "后端",
        "全栈",
        "测试",
        "运维",
        "安全",
        "爬虫",
        "可视化",
        "算法",
        "数据库",
        "中间件",
        "架构",
        "微服务",
        "分布式",
        "高并发",
        "高可用",
        "云原生",
        "大数据",
        "数据分析",
        "数据挖掘",
        "数据仓库",
        "数据治理",
        "机器学习",
        "深度学习",
        "自然语言处理",
        "计算机视觉",
        "推荐系统",
        "知识图谱",
    }

    cn_re = re.compile(r"^[\u4e00-\u9fff]{2,8}$")
    tech_suffix = (
        "开发",
        "测试",
        "运维",
        "安全",
        "算法",
        "架构",
        "平台",
        "框架",
        "协议",
        "数据库",
        "中间件",
        "模型",
        "工程",
        "服务",
        "集群",
        "云",
        "仓库",
        "治理",
        "挖掘",
        "分析",
        "可视化",
        "爬虫",
    )

    def keep_token(x: str) -> bool:
        if not x:
            return False
        if x in deny:
            return False
        if x in allow_cn:
            return True
        if cn_re.fullmatch(x):
            if x.endswith(("能力", "意识", "精神", "心", "态度")):
                return False
            if x.endswith(tech_suffix):
                return True
            if "数据" in x and len(x) >= 3:
                return True
            return False
        return True

    def to_token_set(v) -> List[str]:
        if v is None:
            return []
        if isinstance(v, list):
            xs = [str(x or "").strip().lower() for x in v]
        else:
            xs = [str(x or "").strip().lower() for x in str(v).split()]
        out = []
        seen = set()
        for x in xs:
            if not x:
                continue
            if len(x) < 2:
                continue
            if not keep_token(x):
                continue
            if x in seen:
                continue
            seen.add(x)
            out.append(x)
        return out

    tmp["_tok"] = tmp[token_col].map(to_token_set)
    all_tokens = [t for xs in tmp["_tok"].tolist() for t in (xs or [])]
    if not all_tokens:
        return {}

    skill_counts = pd.Series(all_tokens).value_counts()
    skills: List[str] = skill_counts.head(int(top_skills)).index.tolist()
    if not skills:
        return {}

    skill_set = set(skills)
    tmp["_tok_f"] = tmp["_tok"].map(lambda xs: [t for t in (xs or []) if t in skill_set])
    exploded = tmp[["role", "_tok_f"]].explode("_tok_f")
    exploded = exploded[exploded["_tok_f"].notna()].copy()
    if exploded.empty:
        return {}

    grp = exploded.groupby(["role", "_tok_f"], as_index=False).size()
    grp.columns = ["role", "skill", "count"]

    role_index = {r: i for i, r in enumerate(roles)}
    skill_index = {s: i for i, s in enumerate(skills)}
    triples = [[int(skill_index[r["skill"]]), int(role_index[r["role"]]), int(r["count"])] for _, r in grp.iterrows()]

    matrix = np.zeros((len(roles), len(skills)), dtype=np.int32)
    for x, y, v in triples:
        if 0 <= y < matrix.shape[0] and 0 <= x < matrix.shape[1]:
            matrix[y, x] = v
    nonzero = matrix[matrix > 0]
    max_clip = int(np.percentile(nonzero, 95)) if nonzero.size else 0

    payload = {
        "x": skills,
        "y": roles,
        "data": triples,
        "max": int(matrix.max()) if matrix.size else 0,
        "max_clip": int(max_clip),
        "token_col": token_col,
    }

    p_json = os.path.join(out_dir, "job_skill_heatmap.json")
    with open(p_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)

    return {"job_skill_heatmap_json": p_json}


def run_cluster(
    df: pd.DataFrame,
    out_dir: str,
    n_clusters: int = 8,
    max_features: int = 8000,
    random_state: int = 42,
) -> Dict[str, str]:
    """运行文本聚类分析：TF-IDF 向量化 → MiniBatchKMeans 聚类 → t-SNE 降维可视化。
    生成聚类散点图 PNG，不同颜色表示不同主题簇，图例显示各簇 Top 关键词。
    返回产物路径字典。"""
    _require_sklearn()
    _require_matplotlib()

    from sklearn.cluster import MiniBatchKMeans
    from sklearn.decomposition import TruncatedSVD
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.manifold import TSNE

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

    n_samples = int(X.shape[0])
    n_features = int(X.shape[1])

    km = MiniBatchKMeans(n_clusters=int(n_clusters), random_state=int(random_state), batch_size=2048, n_init=10)
    labels = km.fit_predict(X)

    df2["cluster_id"] = labels.astype(int)

    sample_n = min(5000, n_samples)
    if n_samples > sample_n:
        df_plot = df2.sample(n=sample_n, random_state=int(random_state)).copy()
        X_plot = X[df_plot.index]
    else:
        df_plot = df2.copy()
        X_plot = X

    reduce_name = "tsne2"
    Z = None
    try:
        svd_k = max(2, min(60, n_features - 1, 50))
        svd = TruncatedSVD(n_components=int(svd_k), random_state=int(random_state))
        X_low = svd.fit_transform(X_plot)
        n_tsne = int(X_low.shape[0])
        perplexity = max(5, min(40, n_tsne // 50))
        perplexity = min(perplexity, max(5, (n_tsne - 1) // 3))
        try:
            tsne = TSNE(
                n_components=2,
                perplexity=float(perplexity),
                init="pca",
                learning_rate="auto",
                random_state=int(random_state),
                n_iter=1200,
            )
        except TypeError:
            tsne = TSNE(
                n_components=2,
                perplexity=float(perplexity),
                init="pca",
                learning_rate="auto",
                random_state=int(random_state),
                max_iter=1200,
            )
        Z = tsne.fit_transform(X_low)
    except Exception:
        reduce_name = "svd2"
        try:
            reducer = TruncatedSVD(n_components=2, random_state=int(random_state))
            Z = reducer.fit_transform(X_plot)
        except Exception:
            Z = None

    if Z is None:
        return {}

    df_plot["dim1"] = Z[:, 0]
    df_plot["dim2"] = Z[:, 1]

    feature_names = vectorizer.get_feature_names_out()
    centers = km.cluster_centers_
    cluster_topics = {}
    for cid in range(int(n_clusters)):
        if cid >= centers.shape[0]:
            cluster_topics[cid] = []
            continue
        row = centers[cid]
        top_idx = row.argsort()[::-1][:5]
        cluster_topics[cid] = [str(feature_names[i]) for i in top_idx if i < len(feature_names)]

    paths = {}

    cmap_name = "tab10" if int(n_clusters) <= 10 else "tab20"
    cmap = plt.get_cmap(cmap_name)

    fig = plt.figure(figsize=(12, 7))
    ax = fig.add_subplot(111)
    for cid in range(int(n_clusters)):
        sub = df_plot[df_plot["cluster_id"] == cid]
        if sub.empty:
            continue
        color = cmap(cid % cmap.N)
        topic = cluster_topics.get(cid) or []
        topic_text = "/".join(topic[:3]) if topic else "（无关键词）"
        label = f"主题{cid + 1}: {topic_text}"
        ax.scatter(sub["dim1"], sub["dim2"], s=9, color=color, alpha=0.62, label=label, linewidths=0)

    ax.set_title(f"招聘岗位文本主题聚类（{reduce_name}+KMeans，K={n_clusters}，采样={len(df_plot)}/{n_samples}）")
    ax.set_xlabel("降维维度1")
    ax.set_ylabel("降维维度2")
    ax.grid(True, linestyle="--", linewidth=0.6, alpha=0.18)

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


def export_skill_timeline(
    df: pd.DataFrame,
    out_dir: str,
    top_skills: int = 10,
    token_col_candidates: Tuple[str, ...] = ("tokens_core", "tokens_skill", "tokens"),
) -> Dict[str, str]:
    """生成技术招聘趋势时间线 JSON。
    按 publish_date 聚合到月维度，统计 top_skills 个核心技术关键词
    在各月份的岗位出现频次，输出供前端 ECharts Line 渲染的多系列时序数据。
    返回产物路径字典。"""
    os.makedirs(out_dir, exist_ok=True)

    if df is None or df.empty:
        return {}
    if "publish_date" not in df.columns:
        return {}

    token_col: Optional[str] = None
    for c in token_col_candidates:
        if c in df.columns:
            token_col = c
            break
    if token_col is None:
        return {}

    tmp = df.copy()
    tmp["dt"] = pd.to_datetime(tmp["publish_date"], errors="coerce")
    tmp = tmp.dropna(subset=["dt"]).copy()
    if tmp.empty:
        return {}

    tmp["ym"] = tmp["dt"].dt.to_period("M").astype(str)
    months = sorted(tmp["ym"].unique())

    all_tokens = []
    for v in tmp[token_col]:
        if v is None:
            continue
        if isinstance(v, list):
            all_tokens.extend([str(x or "").strip().lower() for x in v])
        else:
            all_tokens.extend([str(x or "").strip().lower() for x in str(v).split()])
    if not all_tokens:
        return {}

    token_counts = pd.Series(all_tokens).value_counts()
    top_tokens = [t for t in token_counts.head(int(top_skills)).index.tolist() if len(t) >= 2]

    series_list = []
    for token in top_tokens:
        data = []
        for m in months:
            sub = tmp[tmp["ym"] == m]
            cnt = 0
            for v in sub[token_col]:
                if v is None:
                    continue
                xs = v if isinstance(v, list) else str(v).split()
                if any(str(x or "").strip().lower() == token for x in xs):
                    cnt += 1
            data.append(cnt)
        series_list.append({"name": token, "data": data})

    payload = {
        "months": months,
        "series": series_list,
    }

    p_json = os.path.join(out_dir, "skill_timeline.json")
    with open(p_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)

    return {"skill_timeline_json": p_json}


def export_company_size_salary_bar(
    df: pd.DataFrame,
    out_dir: str,
    min_count: int = 10,
) -> Dict[str, str]:
    """生成公司规模-薪资柱状图数据 JSON。
    将公司规模字段归一到标准区间（0-20人/20-99人/.../10000+人），
    计算各区间的平均薪资与岗位数量，输出供前端 ECharts Bar 渲染的数据。
    返回产物路径字典。"""
    os.makedirs(out_dir, exist_ok=True)

    if df is None or df.empty:
        return {}
    if "company_size" not in df.columns or "salary_avg" not in df.columns:
        return {}

    def norm_company_size(v) -> str:
        s = str(v or "").strip()
        if not s or s.lower() in {"nan", "none"}:
            return "未知"
        s = re.sub(r"\s+", "", s)
        s = s.replace("人以上", "+人").replace("以上", "+").replace("人", "人")

        m = re.search(r"(\d+)\s*[-~—–]\s*(\d+)", s)
        if m:
            a = int(m.group(1))
            b = int(m.group(2))
            size_val = b
        else:
            m2 = re.search(r"(\d+)\s*\+?", s)
            if m2 and ("+" in s or "以上" in s):
                a = int(m2.group(1))
                size_val = a
            else:
                return s

        bins = [
            (0, 20, "0-20人"),
            (20, 100, "20-99人"),
            (100, 500, "100-499人"),
            (500, 1000, "500-999人"),
            (1000, 10000, "1000-9999人"),
            (10000, None, "10000+人"),
        ]
        for lo, hi, label in bins:
            if hi is None:
                if size_val >= lo:
                    return label
            else:
                if lo <= size_val < hi:
                    return label
        return "未知"

    tmp = df[["company_size", "salary_avg"]].copy()
    tmp["salary_avg"] = pd.to_numeric(tmp["salary_avg"], errors="coerce")
    tmp = tmp.dropna(subset=["salary_avg"]).copy()
    tmp = tmp[(tmp["salary_avg"] > 0) & (tmp["salary_avg"] <= 200)].copy()
    if tmp.empty:
        return {}

    tmp["company_size_norm"] = tmp["company_size"].map(norm_company_size)
    tmp["company_size_norm"] = tmp["company_size_norm"].astype(str).replace({"": "未知"}).fillna("未知")

    grp = (
        tmp.groupby("company_size_norm", as_index=False)
        .agg(avg_salary=("salary_avg", "mean"), count=("salary_avg", "size"))
    )
    grp["avg_salary"] = grp["avg_salary"].round(2)
    grp = grp[grp["count"] >= int(min_count)].copy()
    if grp.empty:
        return {}

    order = ["0-20人", "20-99人", "100-499人", "500-999人", "1000-9999人", "10000+人", "未知"]
    order_idx = {k: i for i, k in enumerate(order)}
    grp["_ord"] = grp["company_size_norm"].map(lambda x: order_idx.get(str(x), 999))
    grp = grp.sort_values(by=["_ord", "count"], ascending=[True, False]).drop(columns=["_ord"]).reset_index(drop=True)

    payload = {
        "x": grp["company_size_norm"].astype(str).tolist(),
        "avg_salary": grp["avg_salary"].astype(float).tolist(),
        "count": grp["count"].astype(int).tolist(),
        "unit": "K",
    }

    p_json = os.path.join(out_dir, "company_size_salary.json")
    with open(p_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)

    return {"company_size_salary_json": p_json}


def export_edu_exp_salary_bubble(
    df: pd.DataFrame,
    out_dir: str,
    min_count: int = 5,
    max_exp: int = 14,
    max_edu: int = 8,
) -> Dict[str, str]:
    """生成学历×经验薪资气泡图数据 JSON。
    将学历和经验字段归一到标准类别，按 education×experience 聚合计算
    平均薪资（颜色映射）和岗位数量（气泡大小），输出供前端 ECharts Scatter 渲染的数据。
    返回产物路径字典。"""
    os.makedirs(out_dir, exist_ok=True)

    if df is None or df.empty:
        return {}
    if "education" not in df.columns or "experience" not in df.columns or "salary_avg" not in df.columns:
        return {}

    def norm_edu(v) -> str:
        s = str(v or "").strip()
        if not s or s.lower() in {"nan", "none"}:
            return "未知"
        if "不限" in s:
            return "不限"
        if "博士" in s:
            return "博士"
        if "硕士" in s:
            return "硕士"
        if "本科" in s:
            return "本科"
        if "大专" in s or "专科" in s:
            return "大专"
        if "中专" in s or "中技" in s:
            return "中专/中技"
        if "高中" in s or "初中" in s or "中学" in s:
            return "高中及以下"
        return s

    def exp_weight_and_label(v) -> Tuple[int, str]:
        s = str(v or "").strip()
        if not s or s.lower() in {"nan", "none"}:
            return (999, "未知")
        if "应届" in s or "在校" in s:
            return (-1, "应届/在校")
        if "不限" in s or "无需" in s:
            return (0, "不限")

        s2 = re.sub(r"\s+", "", s)
        m = re.search(r"(\d+)\s*[-~—–至]\s*(\d+)", s2)
        if m:
            a = int(m.group(1))
            b = int(m.group(2))
            lo = min(a, b)
            return (lo + 1, f"{lo}-{max(a, b)}年")
        m2 = re.search(r"(\d+)", s2)
        if m2:
            a = int(m2.group(1))
            if "+" in s2 or "以上" in s2:
                return (a + 1, f"{a}+年")
            return (a + 1, f"{a}年")
        return (998, s)

    tmp = df[["education", "experience", "salary_avg"]].copy()
    tmp["salary_avg"] = pd.to_numeric(tmp["salary_avg"], errors="coerce")
    tmp = tmp.dropna(subset=["salary_avg"]).copy()
    tmp = tmp[(tmp["salary_avg"] > 0) & (tmp["salary_avg"] <= 200)].copy()
    if tmp.empty:
        return {}

    tmp["edu_norm"] = tmp["education"].map(norm_edu)
    exp_w = tmp["experience"].map(exp_weight_and_label)
    tmp["exp_w"] = exp_w.map(lambda x: int(x[0]) if isinstance(x, tuple) and len(x) == 2 else 999)
    tmp["exp_norm"] = exp_w.map(lambda x: str(x[1]) if isinstance(x, tuple) and len(x) == 2 else "未知")

    grp = (
        tmp.groupby(["edu_norm", "exp_norm"], as_index=False)
        .agg(avg_salary=("salary_avg", "mean"), count=("salary_avg", "size"), exp_w=("exp_w", "min"))
    )
    grp["avg_salary"] = grp["avg_salary"].round(2)
    grp = grp[grp["count"] >= int(min_count)].copy()
    if grp.empty:
        return {}

    edu_order = ["不限", "高中及以下", "中专/中技", "大专", "本科", "硕士", "博士", "未知"]
    edu_idx = {k: i for i, k in enumerate(edu_order)}
    edu_vals = (
        grp[["edu_norm", "count"]]
        .groupby("edu_norm", as_index=False)
        .agg(total=("count", "sum"))
    )
    edu_vals["_ord"] = edu_vals["edu_norm"].map(lambda x: edu_idx.get(str(x), 999))
    edu_vals = edu_vals.sort_values(by=["_ord", "total"], ascending=[True, False]).drop(columns=["_ord"])
    edu_list = edu_vals["edu_norm"].astype(str).tolist()[: int(max_edu)]

    exp_vals = (
        grp[["exp_norm", "count", "exp_w"]]
        .groupby("exp_norm", as_index=False)
        .agg(total=("count", "sum"), w=("exp_w", "min"))
        .sort_values(by=["w", "total"], ascending=[True, False])
    )
    exp_list = exp_vals["exp_norm"].astype(str).tolist()[: int(max_exp)]

    grp = grp[grp["edu_norm"].isin(edu_list) & grp["exp_norm"].isin(exp_list)].copy()
    if grp.empty:
        return {}

    exp_index = {x: i for i, x in enumerate(exp_list)}
    edu_index = {y: i for i, y in enumerate(edu_list)}

    data = []
    max_count = int(grp["count"].max()) if not grp.empty else 0
    salary_min = float(grp["avg_salary"].min()) if not grp.empty else 0.0
    salary_max = float(grp["avg_salary"].max()) if not grp.empty else 0.0
    for _, r in grp.iterrows():
        xi = exp_index.get(str(r["exp_norm"]))
        yi = edu_index.get(str(r["edu_norm"]))
        if xi is None or yi is None:
            continue
        data.append([int(xi), int(yi), int(r["count"]), float(r["avg_salary"])])

    payload = {
        "x": exp_list,
        "y": edu_list,
        "data": data,
        "max_count": max_count,
        "salary_min": round(salary_min, 2),
        "salary_max": round(salary_max, 2),
        "unit": "K",
    }

    p_json = os.path.join(out_dir, "edu_exp_salary_bubble.json")
    with open(p_json, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)

    return {"edu_exp_salary_bubble_json": p_json}


def _require_sklearn():
    """检查 scikit-learn 是否已安装，未安装时抛出 RuntimeError。"""
    try:
        import sklearn  # noqa: F401
    except Exception as e:
        raise RuntimeError("缺少依赖: scikit-learn。请先在 crawler 目录执行: pip install -r requirements.txt") from e


def _require_matplotlib():
    """检查 matplotlib 是否已安装，设置 Agg 后端（无 GUI）并配置中文字体，未安装时抛出 RuntimeError。"""
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
