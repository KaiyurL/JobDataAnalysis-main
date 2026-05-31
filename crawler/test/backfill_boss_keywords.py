"""
回填更新 BOSS(job_info) 历史数据的 job_keywords / company_size / company_industry / company_welfare

- 打开岗位详情页
- 从 ul.job-keyword-list > li 提取技能标签
- 从详情页公司信息区提取公司规模/所属行业
- 从详情页福利标签区提取公司福利（无标签则从职位描述中推导）
- 更新到 job_info 对应字段

运行示例：
- 只回填关键词（默认全部字段）：python backfill_boss_keywords.py --browser chrome
- 只回填公司信息：python backfill_boss_keywords.py --fields company --browser chrome
- 分批：python backfill_boss_keywords.py --start-id 1 --limit 100 --browser chrome
- 默认并发抓取（workers=2）：python backfill_boss_keywords.py --mode null-only --workers 2
"""

import argparse
import html as _html
import os
import random
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Dict, List, Optional, Set, Tuple

import psycopg2
import requests
from DrissionPage import ChromiumOptions, ChromiumPage
from DrissionPage.errors import BrowserConnectError


def _import_common():
    import sys
    from pathlib import Path
    
    # 添加 crawler 目录到 Python 路径
    crawler_dir = Path(__file__).resolve().parent.parent
    if str(crawler_dir) not in sys.path:
        sys.path.insert(0, str(crawler_dir))
    
    try:
        from spider_call.spider_common import find_browser_path, get_db_config  # type: ignore

        return find_browser_path, get_db_config
    except Exception:
        from crawler.spider_call.spider_common import find_browser_path, get_db_config  # type: ignore

        return find_browser_path, get_db_config


_WELFARE_TERMS = re.compile(
    r"五险一金|六险一金|补充医疗|商业保险|带薪年假|年终奖|绩效奖金|节日福利|定期体检|周末双休|双休|弹性工作|加班补助|餐补|交通补贴|通讯补贴|住房补贴|住房公积金|员工旅游|团建|下午茶|免费班车|股票期权|入职培训|晋升|落户"
)


def _open_browser(browser: str) -> ChromiumPage:
    find_browser_path, _ = _import_common()
    browser_path = find_browser_path(browser)
    if not browser_path:
        raise RuntimeError("未找到可用的浏览器可执行文件(Edge/Chrome/Chromium)。")

    tmp_path = os.path.join(os.path.dirname(__file__), "tmp")
    os.makedirs(tmp_path, exist_ok=True)

    options = ChromiumOptions(read_file=False)
    options.set_browser_path(browser_path)
    options.auto_port(tmp_path=tmp_path)
    try:
        return ChromiumPage(addr_or_opts=options)
    except BrowserConnectError:
        options2 = ChromiumOptions(read_file=False)
        options2.set_browser_path(browser_path)
        options2.set_local_port(9222)
        options2.existing_only(True)
        return ChromiumPage(addr_or_opts=options2)


def _is_login_page(page) -> bool:
    try:
        u = str(getattr(page, "url", "") or "")
    except Exception:
        u = ""
    try:
        title = str(page.run_js("return document.title") or "")
    except Exception:
        title = ""
    try:
        h = str(page.html or "")
    except Exception:
        h = ""

    if "/web/user" in u:
        return True
    if "注册登录" in title or "登录/注册" in title:
        return True
    if "login-page" in h or "user-login" in h or "zhipin-sign" in h:
        return True
    return False


def _get_cookies_and_ua(page) -> Tuple[Dict[str, str], str]:
    cookies = {}
    try:
        for c in page.cookies() or []:
            name = c.get("name")
            value = c.get("value")
            if name and value is not None:
                cookies[str(name)] = str(value)
    except Exception:
        cookies = {}

    ua = ""
    try:
        ua = str(page.run_js("return navigator.userAgent") or "")
    except Exception:
        ua = ""

    return cookies, ua


def _build_boss_headers(user_agent: str) -> Dict[str, str]:
    ua = str(user_agent or "").strip()
    if not ua:
        ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    return {
        "User-Agent": ua,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Cache-Control": "no-cache",
        "Pragma": "no-cache",
        "Connection": "keep-alive",
    }


def _is_login_html(final_url: str, html_text: str) -> bool:
    u = str(final_url or "")
    h = str(html_text or "")
    if "/web/user" in u:
        return True
    if "【BOSS直聘注册登录】" in h or "login-page" in h or "user-login" in h or "zhipin-sign" in h:
        return True
    return False


def _strip_tags(s: str) -> str:
    t = re.sub(r"<script[^>]*>[\s\S]*?</script>", " ", s, flags=re.IGNORECASE)
    t = re.sub(r"<style[^>]*>[\s\S]*?</style>", " ", t, flags=re.IGNORECASE)
    t = re.sub(r"<[^>]+>", " ", t)
    t = _html.unescape(t)
    t = re.sub(r"\s+", " ", t).strip()
    return t


def _extract_block(html_text: str, class_hint: str, window: int = 12000) -> str:
    h = str(html_text or "")
    idx = h.find(class_hint)
    if idx < 0:
        return ""
    start = max(0, idx - window // 4)
    end = min(len(h), idx + window)
    return h[start:end]


def _extract_job_keyword_tags(page) -> Optional[List[str]]:
    kw_items: List[str] = []
    for sel in (
        'xpath://ul[contains(@class, "job-keyword-list")]/li',
        "css:ul.job-keyword-list > li",
    ):
        try:
            eles = page.eles(sel, timeout=1)
        except Exception:
            eles = []
        for e in eles or []:
            try:
                t = str(e.text or "").replace("\n", "").strip()
            except Exception:
                t = ""
            if not t:
                continue
            t = t.replace("BOSS直聘", "").strip()
            if not t:
                continue
            if t not in kw_items:
                kw_items.append(t)
    return kw_items if kw_items else None


def _extract_job_desc(page) -> Optional[str]:
    for sel in (
        'xpath://div[contains(@class, "job-sec-text")]',
        'xpath://div[contains(@class, "job-detail-section")]//div[contains(@class, "text")]',
        'xpath://div[contains(@class, "job-detail")]',
    ):
        try:
            ele = page.ele(sel, timeout=2)
            text = str(ele.text or "").strip() if ele else ""
            if text:
                return text
        except Exception:
            continue
    return None


def _extract_company_info(page) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    company_size = None
    company_industry = None
    company_welfare = None

    tags: List[str] = []
    for sel in (
        'xpath://div[contains(@class, "job-company")]//*[self::span or self::a][contains(@class, "company") or contains(@class, "text") or contains(@class, "tag") or contains(@class, "info")]',
        'xpath://div[contains(@class, "company-info")]//*[self::span or self::a or self::p]',
        'xpath://div[contains(@class, "sider-company")]//*[self::span or self::a or self::p]',
    ):
        try:
            eles = page.eles(sel, timeout=1)
        except Exception:
            eles = []
        for e in eles or []:
            try:
                t = str(e.text or "").strip()
            except Exception:
                t = ""
            if t and t not in tags:
                tags.append(t)

    if tags:
        for t in tags:
            if "人" in t and (t.endswith("人") or t.endswith("人以上") or t.endswith("以上")):
                company_size = company_size or t

        for t in tags:
            if not t:
                continue
            if company_size and t == company_size:
                continue
            if "融资" in t or "上市" in t or "未融资" in t:
                continue
            if "人" in t:
                continue
            if len(t) > 20:
                continue
            company_industry = company_industry or t

    welfare_tags: List[str] = []
    for sel in (
        'xpath://*[(self::div or self::section) and (contains(., "职位福利") or contains(., "福利"))]//span[contains(@class, "tag") or contains(@class, "item") or contains(@class, "text")]',
        'xpath://div[contains(@class, "job-tags")]//span',
        'xpath://div[contains(@class, "job-welfare")]//span',
    ):
        try:
            eles = page.eles(sel, timeout=1)
        except Exception:
            eles = []
        for e in eles or []:
            try:
                t = str(e.text or "").strip()
            except Exception:
                t = ""
            if t and t not in welfare_tags:
                welfare_tags.append(t)

    if welfare_tags:
        company_welfare = ",".join(welfare_tags[:30])
    else:
        desc = _extract_job_desc(page)
        company_welfare = _extract_welfare_from_desc(desc)

    return company_size, company_industry, company_welfare


def _extract_welfare_from_desc(job_desc) -> Optional[str]:
    if not isinstance(job_desc, str):
        return None
    text = re.sub(r"\r\n?", "\n", job_desc).strip()
    if not text:
        return None
    if not re.search(r"福利|待遇|薪酬福利|员工福利|我们提供|公司提供", text):
        return None

    heading = re.compile(r"(?im)^(?:\s*)(福利待遇|薪酬福利|员工福利|公司福利|福利|待遇)\s*[:：]?")
    stop_heading = re.compile(
        r"(?im)^(?:\s*)(岗位职责|工作职责|职责描述|工作内容|职位描述|任职要求|岗位要求|任职资格|职位要求|职位亮点|工作地点|联系方式|公司介绍|企业介绍)\s*[:：]?"
    )

    m = heading.search(text)
    if m:
        start = m.start()
        tail = text[start:]
        m2 = stop_heading.search(tail[m.end() - start :])
        if m2:
            end = (m.end() - start) + m2.start()
            block = tail[:end]
        else:
            block = tail
        block = re.sub(r"\n{3,}", "\n\n", block).strip()
        return block if block else None

    lines = [ln.strip() for ln in text.split("\n") if ln.strip()]
    if not lines:
        return None
    keep = []
    for ln in lines:
        if re.search(r"福利|待遇|我们提供|公司提供", ln) or _WELFARE_TERMS.search(ln):
            keep.append(ln)
    if not keep:
        return None
    merged = "；".join(dict.fromkeys(keep))
    merged = re.sub(r"[；\s]+$", "", merged).strip()
    return merged if merged else None


def _extract_job_keyword_tags_from_html(html_text: str) -> Optional[List[str]]:
    h = str(html_text or "")
    m = re.search(r'<ul[^>]*class="[^"]*job-keyword-list[^"]*"[^>]*>([\s\S]*?)</ul>', h)
    if not m:
        return None
    ul_inner = m.group(1)
    items = []
    for raw in re.findall(r"<li[^>]*>([\s\S]*?)</li>", ul_inner):
        t = _strip_tags(raw)
        t = t.replace("BOSS直聘", "").strip()
        if t and t not in items:
            items.append(t)
    return items if items else None


def _extract_job_desc_from_html(html_text: str) -> Optional[str]:
    h = str(html_text or "")
    for pat in (
        r'<div[^>]*class="[^"]*job-sec-text[^"]*"[^>]*>([\s\S]*?)</div>',
        r'<div[^>]*class="[^"]*job-detail-section[^"]*"[^>]*>[\s\S]*?<div[^>]*class="[^"]*text[^"]*"[^>]*>([\s\S]*?)</div>',
        r'<div[^>]*class="[^"]*job-detail[^"]*"[^>]*>([\s\S]*?)</div>',
    ):
        m = re.search(pat, h)
        if m:
            t = _strip_tags(m.group(1))
            if t:
                return t
    return None


def _extract_company_info_from_html(html_text: str) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    h = str(html_text or "")
    company_size = None
    company_industry = None
    company_welfare = None

    snippet = (
        _extract_block(h, "job-company")
        or _extract_block(h, "company-info")
        or _extract_block(h, "sider-company")
    )
    tags_text = _strip_tags(snippet) if snippet else ""
    candidates = [x.strip() for x in re.split(r"[|/·,，\s]+", tags_text) if x.strip()]
    uniq = []
    for c in candidates:
        if c not in uniq:
            uniq.append(c)

    if uniq:
        for t in uniq:
            if "人" in t and (t.endswith("人") or t.endswith("人以上") or t.endswith("以上")):
                company_size = company_size or t

        for t in uniq:
            if not t:
                continue
            if company_size and t == company_size:
                continue
            if "融资" in t or "上市" in t or "未融资" in t:
                continue
            if "人" in t:
                continue
            if len(t) > 20:
                continue
            company_industry = company_industry or t

    welfare_tags = []
    for cls in ("job-tags", "job-welfare"):
        block = _extract_block(h, cls)
        if not block:
            continue
        for raw in re.findall(r"<span[^>]*>([\s\S]*?)</span>", block):
            t = _strip_tags(raw)
            if t and t not in welfare_tags:
                welfare_tags.append(t)

    if welfare_tags:
        company_welfare = ",".join(welfare_tags[:30])
    else:
        desc = _extract_job_desc_from_html(h)
        company_welfare = _extract_welfare_from_desc(desc)

    return company_size, company_industry, company_welfare


def _fetch_targets(
    conn,
    start_id: int,
    limit: int,
    mode: str,
    fields: Set[str],
) -> List[Tuple[int, str]]:
    sql = "SELECT id, job_url FROM job_info WHERE job_url IS NOT NULL AND job_url <> '' AND id >= %s"
    params = [int(start_id)]

    conditions: List[str] = []
    if "keywords" in fields:
        if mode == "null-only":
            conditions.append("(job_keywords IS NULL OR job_keywords = '')")
    if "company" in fields:
        if mode == "null-only":
            conditions.append("(company_size IS NULL OR company_industry IS NULL OR company_welfare IS NULL)")

    if conditions:
        sql += " AND (" + " OR ".join(conditions) + ")"

    sql += " ORDER BY id ASC"
    if limit > 0:
        sql += " LIMIT %s"
        params.append(int(limit))

    with conn.cursor() as cur:
        cur.execute(sql, tuple(params))
        rows = cur.fetchall()
    out = []
    for r in rows or []:
        try:
            rid = int(r[0])
            url = str(r[1] or "").strip()
        except Exception:
            continue
        if rid > 0 and url:
            out.append((rid, url))
    return out


@dataclass
class _FetchResult:
    job_id: int
    ok: bool
    need_login: bool
    keywords: Optional[str]
    company_size: Optional[str]
    company_industry: Optional[str]
    company_welfare: Optional[str]
    error: Optional[str] = None
    status_code: Optional[int] = None


def _fetch_and_parse_one(
    job_id: int,
    job_url: str,
    cookies: Dict[str, str],
    headers: Dict[str, str],
    timeout: int,
    fields: Set[str],
) -> _FetchResult:
    url = str(job_url).strip("`").strip()
    try:
        resp = requests.get(url, headers=headers, cookies=cookies, timeout=timeout, allow_redirects=True)
    except Exception as ex:
        return _FetchResult(
            job_id=job_id,
            ok=False,
            need_login=False,
            keywords=None,
            company_size=None,
            company_industry=None,
            company_welfare=None,
            error=str(ex),
        )

    final_url = str(resp.url or url)
    html_text = ""
    try:
        html_text = resp.text or ""
    except Exception:
        html_text = ""

    if _is_login_html(final_url, html_text):
        return _FetchResult(
            job_id=job_id,
            ok=False,
            need_login=True,
            keywords=None,
            company_size=None,
            company_industry=None,
            company_welfare=None,
            status_code=int(getattr(resp, "status_code", 0) or 0),
        )

    kw_str = None
    company_size = None
    company_industry = None
    company_welfare = None

    if "keywords" in fields:
        kw_tags = _extract_job_keyword_tags_from_html(html_text)
        if kw_tags:
            kw_str = ",".join([str(x).strip() for x in kw_tags if str(x).strip()][:30])

    if "company" in fields:
        company_size, company_industry, company_welfare = _extract_company_info_from_html(html_text)

    return _FetchResult(
        job_id=job_id,
        ok=True,
        need_login=False,
        keywords=kw_str,
        company_size=company_size,
        company_industry=company_industry,
        company_welfare=company_welfare,
        status_code=int(getattr(resp, "status_code", 0) or 0),
    )


def _update_job_fields(
    conn,
    job_id: int,
    keywords: Optional[str],
    company_size: Optional[str],
    company_industry: Optional[str],
    company_welfare: Optional[str],
    fields: Set[str],
) -> None:
    set_parts: List[str] = []
    params: List = []
    if "keywords" in fields:
        set_parts.append("job_keywords = %s")
        params.append(keywords)
    if "company" in fields:
        set_parts.append("company_size = %s")
        params.append(company_size)
        set_parts.append("company_industry = %s")
        params.append(company_industry)
        set_parts.append("company_welfare = %s")
        params.append(company_welfare)
    if not set_parts:
        return
    params.append(int(job_id))
    sql = "UPDATE job_info SET " + ", ".join(set_parts) + " WHERE id = %s"
    with conn.cursor() as cur:
        cur.execute(sql, tuple(params))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--browser", choices=["auto", "edge", "chrome"], default="auto")
    ap.add_argument("--mode", choices=["all", "null-only"], default="all")
    ap.add_argument("--fields", choices=["keywords", "company", "all"], default="all")
    ap.add_argument("--fetch-mode", choices=["requests", "browser"], default="requests")
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--start-id", type=int, default=1)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--commit-every", type=int, default=100)
    ap.add_argument("--delay-min", type=float, default=0.2)
    ap.add_argument("--delay-max", type=float, default=0.8)
    ap.add_argument("--load-wait", type=float, default=0.8)
    ap.add_argument("--http-timeout", type=int, default=15)
    args = ap.parse_args()

    fields_arg = str(args.fields)
    if fields_arg == "all":
        fields: Set[str] = {"keywords", "company"}
    elif fields_arg == "keywords":
        fields = {"keywords"}
    else:
        fields = {"company"}

    find_browser_path, get_db_config = _import_common()
    db_cfg = get_db_config()
    conn = psycopg2.connect(
        host=db_cfg.get("host"),
        port=int(db_cfg.get("port") or 5432),
        user=db_cfg.get("user"),
        password=db_cfg.get("password"),
        dbname=db_cfg.get("database"),
    )

    page = _open_browser(args.browser)
    updated = 0
    failed = 0
    try:
        targets = _fetch_targets(
            conn, start_id=int(args.start_id), limit=int(args.limit),
            mode=str(args.mode), fields=fields,
        )
        print(f"待处理岗位数: {len(targets)} (mode={args.mode}, fields={fields_arg}, start_id={args.start_id}, limit={args.limit or 'ALL'})")
        if not targets:
            return

        print("将打开 BOSS 直聘首页，请确保已登录/通过验证后按回车开始回填...")
        page.get("https://www.zhipin.com")
        time.sleep(2)
        input()

        cookies, ua = _get_cookies_and_ua(page)
        headers = _build_boss_headers(ua)

        remaining = list(targets)
        workers = max(1, int(args.workers or 1))
        delay_min = float(args.delay_min)
        delay_max = float(args.delay_max)
        http_timeout = int(args.http_timeout)

        while remaining:
            processed_ids: Set[int] = set()
            need_login = False
            error_count = 0
            status_fail_count = 0
            print(f"开始批次：remaining={len(remaining)} workers={workers} fetch_mode={args.fetch_mode}")

            if str(args.fetch_mode) == "browser":
                print(f"浏览器模式并发 tab 数: {workers}")
                batch_total = len(remaining)
                batch_index = 0
                while batch_index < len(remaining):
                    if need_login:
                        break
                    batch = remaining[batch_index : batch_index + workers]
                    opened = []
                    try:
                        for job_id, job_url in batch:
                            job_url = str(job_url).strip("`").strip()
                            try:
                                tab = page.new_tab(job_url, background=True)
                            except Exception:
                                try:
                                    tab = page.new_tab("about:blank", background=True)
                                    tab.get(job_url)
                                except Exception:
                                    tab = None
                            opened.append((int(job_id), job_url, tab))

                        time.sleep(max(0.2, float(args.load_wait)))

                        for i, (job_id, job_url, tab) in enumerate(opened, 1):
                            if need_login:
                                break
                            idx = batch_index + i
                            try:
                                print(f"[{idx}/{batch_total}] id={job_id} url={job_url[:80]}")
                                if tab is None:
                                    raise RuntimeError("无法打开新标签页(tab=None)")

                                if _is_login_page(tab):
                                    need_login = True
                                    break

                                kw_str = None
                                company_size = None
                                company_industry = None
                                company_welfare = None
                                if "keywords" in fields:
                                    kw_tags = _extract_job_keyword_tags(tab)
                                    if kw_tags:
                                        kw_str = ",".join([str(x).strip() for x in kw_tags if str(x).strip()][:30])
                                if "company" in fields:
                                    company_size, company_industry, company_welfare = _extract_company_info(tab)

                                _update_job_fields(
                                    conn,
                                    job_id,
                                    kw_str,
                                    company_size,
                                    company_industry,
                                    company_welfare,
                                    fields,
                                )
                                processed_ids.add(int(job_id))
                                updated += 1

                                if int(args.commit_every) > 0 and updated % int(args.commit_every) == 0:
                                    conn.commit()
                                    print(f"已提交 {updated} 条更新")

                                time.sleep(max(0.05, random.uniform(delay_min, delay_max)))
                            except KeyboardInterrupt:
                                print("收到中断，准备退出...")
                                remaining = []
                                break
                            except Exception as ex:
                                failed += 1
                                error_count += 1
                                print("失败:", ex)
                    finally:
                        for _, _, tab in opened:
                            try:
                                if tab is not None:
                                    tab.close()
                            except Exception:
                                pass

                    if not remaining:
                        break
                    batch_index += workers
            else:
                with ThreadPoolExecutor(max_workers=workers) as ex:
                    future_map = {}
                    for job_id, job_url in remaining:
                        fut = ex.submit(
                            _fetch_and_parse_one,
                            int(job_id),
                            str(job_url),
                            cookies,
                            headers,
                            http_timeout,
                            fields,
                        )
                        future_map[fut] = (int(job_id), str(job_url))

                    for fut in as_completed(list(future_map.keys())):
                        try:
                            res = fut.result()
                        except Exception as ex2:
                            failed += 1
                            error_count += 1
                            print("失败:", ex2)
                            continue

                        if res.need_login:
                            need_login = True
                            status_fail_count += 1
                            print(f"检测到登录/验证页：id={res.job_id} status={res.status_code}")
                            continue

                        if not res.ok:
                            failed += 1
                            error_count += 1
                            print(f"失败: id={res.job_id} err={res.error}")
                            continue

                        _update_job_fields(conn, res.job_id, res.keywords, res.company_size, res.company_industry, res.company_welfare, fields)
                        processed_ids.add(int(res.job_id))
                        updated += 1

                        if int(args.commit_every) > 0 and updated % int(args.commit_every) == 0:
                            conn.commit()
                            print(f"已提交 {updated} 条更新")

                        time.sleep(max(0.05, random.uniform(delay_min, delay_max)))

            if processed_ids:
                conn.commit()

            remaining = [(i, u) for (i, u) in remaining if int(i) not in processed_ids]

            if not remaining:
                break

            if need_login:
                workers = 1
                delay_min = max(delay_min, 1.0)
                delay_max = max(delay_max, 2.5)
                http_timeout = max(http_timeout, 25)
                print("需要重新登录/验证：请在浏览器中完成验证后按回车继续...")
                page.get("https://www.zhipin.com")
                time.sleep(2)
                input()
                cookies, ua = _get_cookies_and_ua(page)
                headers = _build_boss_headers(ua)
                continue

            if error_count + status_fail_count >= max(5, workers * 4):
                workers = 1
                delay_min = max(delay_min, 1.0)
                delay_max = max(delay_max, 2.5)
                http_timeout = max(http_timeout, 25)
                print("检测到失败偏多，自动降速并将并发降为 1 后继续...")
                continue

        conn.commit()
        print(f"完成：updated={updated}, failed={failed}")
    finally:
        try:
            conn.close()
        except Exception:
            pass
        try:
            page.quit()
        except Exception:
            pass


if __name__ == "__main__":
    main()
