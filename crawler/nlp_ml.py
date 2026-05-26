"""招聘数据 NLP：机器学习模块。"""

import json
import os
from typing import Dict, List

import pandas as pd


def make_salary_bins(df: pd.DataFrame, n_bins: int = 5) -> pd.Series:
    """基于 salary_avg 列使用分位数将薪资分为 n_bins 个档次，返回每行对应的分类标签。用于分类监督学习的标签构造。"""
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
    """训练 MLP（多层感知机）薪资分档分类模型。
    使用 TF-IDF 向量化文本特征，可选拼接城市/学历/经验等元特征的 OneHot 编码，
    通过 3 层全连接网络 + BatchNorm + Dropout 进行多分类训练，保存最佳模型和向量化器。
    返回产物文件路径字典。"""
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
    """训练 TextCNN 薪资分档分类模型。
    基于 token 序列构建词表，使用多尺寸卷积核（kernel_size=3,4,5）提取文本局部特征，
    可选拼接城市/学历/经验等元特征的 Embedding，进行多分类训练。
    返回产物文件路径字典。"""
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


def _require_sklearn():
    """检查 scikit-learn 是否已安装，未安装时抛出 RuntimeError。"""
    try:
        import sklearn  # noqa: F401
    except Exception as e:
        raise RuntimeError("缺少依赖: scikit-learn。请先在 crawler 目录执行: pip install -r requirements.txt") from e


def _require_torch():
    """检查 PyTorch 是否已安装，未安装时抛出 RuntimeError。"""
    try:
        import torch  # noqa: F401
    except Exception as e:
        raise RuntimeError("缺少依赖: torch。请先在 crawler 目录执行: pip install -r requirements.txt") from e
