"""
用于核实：BOSS 岗位详情页是否包含 postDescription，以及是否能通过 detail.json 接口拿到 postDescription。

运行示例：
- 在项目根目录：python crawler/verify_boss_postdesc.py --url "https://www.zhipin.com/job_detail/xxx.html"
- 在 crawler 目录：python verify_boss_postdesc.py --url "https://www.zhipin.com/job_detail/xxx.html"
"""

import argparse
import os
import re
import sys
import time
from urllib.parse import urlparse

import requests
from DrissionPage import ChromiumOptions, ChromiumPage
from DrissionPage.errors import BrowserConnectError


def _import_common():
    try:
        from spider_call.spider_common import find_browser_path, get_user_agent_of_pc  # type: ignore
        return find_browser_path, get_user_agent_of_pc
    except Exception:
        from crawler.spider_call.spider_common import find_browser_path, get_user_agent_of_pc  # type: ignore
        return find_browser_path, get_user_agent_of_pc


def _sync_requests_cookies(s: requests.Session, page: ChromiumPage):
    s.cookies.clear()
    try:
        for c in page.cookies() or []:
            name = c.get("name")
            value = c.get("value")
            domain = c.get("domain")
            path = c.get("path") or "/"
            if not name or value is None:
                continue
            if domain:
                s.cookies.set(name, value, domain=domain, path=path)
            else:
                s.cookies.set(name, value, path=path)
    except Exception:
        pass


def _guess_security_id(html: str) -> str:
    if not html:
        return ""
    m = re.search(r'"securityId"\s*:\s*"([^"]+)"', html)
    if m:
        return m.group(1).strip()
    m = re.search(r"securityId=([A-Za-z0-9_-]+)", html)
    if m:
        return m.group(1).strip()
    return ""


def _extract_post_description_from_html(html: str) -> str:
    if not html:
        return ""
    m = re.search(r'"postDescription"\s*:\s*"((?:\\.|[^"\\])*)"', html)
    if not m:
        return ""
    raw = m.group(1)
    try:
        import json as _json

        return _json.loads(f'"{raw}"')
    except Exception:
        return raw


def _is_login_page(page_url: str, html: str, title: str) -> bool:
    u = str(page_url or "")
    t = str(title or "")
    h = str(html or "")
    if "/web/user" in u or "/web/user/" in u:
        return True
    if "注册登录" in t or "登录/注册" in t:
        return True
    if "login-page" in h or "user-login" in h or "zhipin-sign" in h:
        return True
    return False


def _open_browser(browser: str):
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True)
    ap.add_argument("--browser", choices=["auto", "edge", "chrome"], default="auto")
    ap.add_argument("--dump-html", action="store_true")
    ap.add_argument("--dump-dir", default=os.path.join(os.path.dirname(__file__), "output"))
    args = ap.parse_args()

    url = str(args.url).strip().replace("`", "").strip()
    if not url:
        raise SystemExit("缺少 --url")

    page = _open_browser(args.browser)
    try:
        def run_once() -> tuple[str, bool, str, str, str]:
            print("打开页面：", url)
            page.get(url)
            time.sleep(2)

            try:
                html0 = page.html
            except Exception:
                html0 = ""

            try:
                page_url0 = str(getattr(page, "url", "") or "")
            except Exception:
                page_url0 = ""

            try:
                title0 = page.run_js("return document.title") or ""
            except Exception:
                title0 = ""

            found0 = "postDescription" in (html0 or "")
            security_id0 = _guess_security_id(html0)
            return html0, found0, security_id0, page_url0, str(title0)

        def dump_html_to_file(html_text: str, prefix: str) -> str:
            dump_dir = os.path.abspath(str(args.dump_dir or "").strip())
            os.makedirs(dump_dir, exist_ok=True)
            ts = time.strftime("%Y%m%d_%H%M%S")
            p = os.path.join(dump_dir, f"{prefix}_{ts}.html")
            with open(p, "w", encoding="utf-8") as f:
                f.write(html_text or "")
            return p

        html, found, security_id, page_url, title = run_once()
        print("实际打开URL：", page_url or "未知")
        print("页面标题：", title or "未知")

        if _is_login_page(page_url, html, title):
            if args.dump_html:
                try:
                    try:
                        outer = page.run_js("return document.documentElement.outerHTML")
                        if isinstance(outer, str) and outer.strip():
                            html = outer
                    except Exception:
                        pass
                    out_path = dump_html_to_file(html, "boss_login")
                    print("当前为登录/注册页，已导出页面源码：", out_path)
                except Exception as ex:
                    print("导出页面源码失败：", ex)

            print()
            print("当前被跳转到登录/注册页，请在打开的浏览器里完成登录/验证码。")
            print("完成后回到终端按回车继续（会重新加载该详情页）...")
            input()
            html, found, security_id, page_url, title = run_once()
            print("实际打开URL：", page_url or "未知")
            print("页面标题：", title or "未知")

        if args.dump_html:
            try:
                try:
                    outer = page.run_js("return document.documentElement.outerHTML")
                    if isinstance(outer, str) and outer.strip():
                        html = outer
                except Exception:
                    pass
                out_path = dump_html_to_file(html, "boss_detail")
                print("已导出页面源码：", out_path)
            except Exception as ex:
                print("导出页面源码失败：", ex)

        print("页面源码是否包含 postDescription：", "是" if found else "否")

        if found:
            post_desc = _extract_post_description_from_html(html)
            print("从 HTML 提取的 postDescription(前200字)：")
            print((post_desc or "")[:200])

        print("securityId(从 HTML 推断)：", security_id or "未找到")

        if security_id:
            find_browser_path, get_user_agent_of_pc = _import_common()
            s = requests.Session()
            _sync_requests_cookies(s, page)
            s.headers.update(
                {
                    "User-Agent": get_user_agent_of_pc(),
                    "Accept": "application/json, text/plain, */*",
                    "Referer": url,
                    "Origin": "https://www.zhipin.com",
                    "Connection": "close",
                }
            )
            api = "https://www.zhipin.com/wapi/zpgeek/job/detail.json"
            print("请求接口：", api)
            resp = s.get(api, params={"securityId": security_id}, timeout=15)
            print("接口状态码：", resp.status_code)
            ct = resp.headers.get("content-type", "")
            if "application/json" in ct:
                data = resp.json()
                code = data.get("code")
                msg = data.get("message") or data.get("msg") or ""
                print("接口 code：", code, "msg：", msg)
                desc = (((data.get("zpData") or {}).get("jobInfo") or {}).get("postDescription") or "")
                print("接口 postDescription 是否为空：", "否" if str(desc).strip() else "是")
                print("接口 postDescription(前200字)：")
                print(str(desc).strip()[:200])
            else:
                print("接口返回非 JSON(前200字)：")
                print((resp.text or "")[:200])

        u = urlparse(url)
        if u.netloc.endswith("zhipin.com"):
            print("提示：如果接口 code!=0 或返回验证页，请先在浏览器里完成登录/验证后再跑该脚本。")
    finally:
        try:
            page.quit()
        except Exception:
            pass


if __name__ == "__main__":
    main()
