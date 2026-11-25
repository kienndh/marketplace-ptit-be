#!/usr/bin/env python3
"""Đối chiếu route FastAPI (Python, chỉ đọc) với mapping Spring MVC/WebSocket (Java).

Cách dùng (từ thư mục marketplace-ptit-be):
    python scripts/route_parity.py [--python-repo ../student-marketplace-be] [--markdown docs/ROUTE_PARITY.md]

Quy tắc:
- Python: quét app/modules/**/(router*.py|*_router.py|websocket.py); mỗi biến APIRouter(prefix=...) giữ prefix riêng,
  decorator @<biến>.<get|post|put|patch|delete|websocket>("<path>"). HTTP router được include với prefix /api/v1;
  router WebSocket (ws_router) không có prefix.
- Java: @RequestMapping cấp class + @Get/Post/Put/Patch/DeleteMapping cấp method trong src/main/java; WebSocket lấy từ
  registry.addHandler(..., "<path>") trong WebSocketConfig.
- So khớp theo (METHOD, đường dẫn đã chuẩn hoá: mọi {param} -> {}), bỏ dấu / cuối.
Exit code 1 nếu còn route Python chưa có ở Java (Missing).
"""
import argparse
import io
import re
import sys
from pathlib import Path

API_PREFIX = "/api/v1"
METHODS = ("get", "post", "put", "patch", "delete")


def norm(path: str) -> str:
    path = re.sub(r"\{[^}]*\}", "{}", path)
    path = path.replace("**", "*").replace("*", "{}")
    path = re.sub(r"/+", "/", path)
    if len(path) > 1 and path.endswith("/"):
        path = path[:-1]
    return path or "/"


def python_routes(repo: Path):
    routes = []  # (method, path, file)
    files = sorted(
        p for p in (repo / "app" / "modules").rglob("*.py")
        if re.search(r"(router|websocket)", p.name)
    )
    for f in files:
        src = f.read_text(encoding="utf-8")
        prefixes = {}
        for m in re.finditer(r"(\w+)\s*=\s*APIRouter\((.*?)\)\s*$", src, re.M | re.S):
            pm = re.search(r"prefix\s*=\s*[\"']([^\"']*)[\"']", m.group(2))
            prefixes[m.group(1)] = pm.group(1) if pm else ""
        for m in re.finditer(
            r"@(\w+)\.(get|post|put|patch|delete|websocket)\(\s*(f?)[\"']([^\"']*)[\"']", src
        ):
            var, method, _f, path = m.groups()
            if var not in prefixes:
                continue
            is_ws = method == "websocket"
            full = prefixes[var] + path
            if not is_ws:
                full = API_PREFIX + full
            routes.append(("WS" if is_ws else method.upper(), norm(full), str(f.relative_to(repo))))
    return routes


def string_literals(text: str):
    return re.findall(r'"((?:[^"\\]|\\.)*)"', text)


def java_routes(root: Path):
    routes = []  # (method, path, file)
    src_root = root / "src" / "main" / "java"
    for f in sorted(src_root.rglob("*.java")):
        src = f.read_text(encoding="utf-8")
        if "Mapping" not in src and "addHandler" not in src:
            continue
        rel = str(f.relative_to(root))
        # WebSocket
        for m in re.finditer(r"addHandler\([^;]*?\)", src, re.S):
            for lit in string_literals(m.group(0)):
                if lit.startswith("/"):
                    routes.append(("WS", norm(lit), rel))
        class_prefix = ""
        cm = re.search(r"@RequestMapping\(([^)]*)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+)?(?:abstract\s+)?class\s", src)
        if cm:
            lits = string_literals(cm.group(1))
            class_prefix = lits[0] if lits else ""
        for m in re.finditer(r"@(Get|Post|Put|Patch|Delete)Mapping(?:\(((?:[^()]|\([^()]*\))*)\))?", src):
            method = m.group(1).upper()
            args = m.group(2) or ""
            args = re.sub(r"\b(produces|consumes|params|headers|name)\s*=\s*(\{[^}]*\}|\"[^\"]*\"|[\w.]+)", "", args)
            lits = string_literals(args) or [""]
            for lit in lits:
                routes.append((method, norm(class_prefix + lit), rel))
    return routes


def main():
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--python-repo", default="../student-marketplace-be")
    ap.add_argument("--markdown", default=None, help="ghi bảng đối chiếu ra file markdown")
    args = ap.parse_args()

    here = Path(__file__).resolve().parent.parent
    py = python_routes(Path(args.python_repo).resolve())
    jv = java_routes(here)

    py_set = {}
    for method, path, f in py:
        py_set.setdefault((method, path), f)
    jv_set = {}
    for method, path, f in jv:
        jv_set.setdefault((method, path), f)

    http = {k: v for k, v in py_set.items() if k[0] != "WS"}
    ws = {k: v for k, v in py_set.items() if k[0] == "WS"}
    done_http = [k for k in http if k in jv_set]
    missing_http = [k for k in http if k not in jv_set]
    done_ws = [k for k in ws if k in jv_set]
    missing_ws = [k for k in ws if k not in jv_set]
    extra = sorted(k for k in jv_set if k not in py_set)

    lines = []
    lines.append(f"HTTP: {len(done_http)}/{len(http)} | WebSocket: {len(done_ws)}/{len(ws)}")
    lines.append(f"Missing HTTP: {len(missing_http)} | Missing WS: {len(missing_ws)} | Java-only (Lệch/thêm): {len(extra)}")
    for k in missing_http + missing_ws:
        lines.append(f"MISSING {k[0]:6} {k[1]}  <- {py_set[k]}")
    for k in extra:
        lines.append(f"JAVA-ONLY {k[0]:6} {k[1]}  <- {jv_set[k]}")
    print("\n".join(lines))

    if args.markdown:
        md = ["# Đối chiếu route Python ↔ Java", "",
              f"- HTTP: **{len(done_http)}/{len(http)}** | WebSocket: **{len(done_ws)}/{len(ws)}**",
              f"- Missing: {len(missing_http) + len(missing_ws)} | Java-only (lệch/thêm): {len(extra)}",
              "", "Sinh tự động bởi `scripts/route_parity.py`.", "",
              "| Trạng thái | Method | Path | Nguồn Python | Nguồn Java |", "|---|---|---|---|---|"]
        for k in sorted(py_set, key=lambda x: (x[1], x[0])):
            ok = k in jv_set
            md.append(f"| {'Done' if ok else 'Missing'} | {k[0]} | `{k[1]}` | {py_set[k]} | {jv_set.get(k, '')} |")
        for k in extra:
            md.append(f"| Lệch (chỉ có ở Java) | {k[0]} | `{k[1]}` | | {jv_set[k]} |")
        Path(args.markdown).write_text("\n".join(md) + "\n", encoding="utf-8")

    sys.exit(1 if (missing_http or missing_ws) else 0)


if __name__ == "__main__":
    main()
