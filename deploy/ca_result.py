#!/usr/bin/env python3
"""解析 `aliyun ecs DescribeInvocationResults` 的 JSON 输出。

用法：
    aliyun ecs DescribeInvocationResults ... | python3 ca_result.py status
    aliyun ecs DescribeInvocationResults ... | python3 ca_result.py output
    aliyun ecs DescribeInvocationResults ... | python3 ca_result.py both

单独放成文件而不是内联在 workflow 的 run: | 块里，是因为内联多行 Python
必须顶格写，会顶断 YAML 的块标量缩进。
"""
from __future__ import annotations

import base64
import json
import sys


def first_result(payload: dict) -> dict:
    invocation = payload["Invocation"]["InvocationResults"]
    results = invocation["InvocationResult"]
    if not results:
        raise SystemExit("DescribeInvocationResults 返回空结果")
    return results[0]


def main() -> int:
    if len(sys.argv) < 2 or sys.argv[1] not in {"status", "output", "both"}:
        print(__doc__, file=sys.stderr)
        return 2

    mode = sys.argv[1]
    result = first_result(json.load(sys.stdin))

    if mode in {"status", "both"}:
        print(result.get("InvocationStatus", ""))

    if mode in {"output", "both"}:
        raw = result.get("Output", "") or ""
        sys.stdout.write(base64.b64decode(raw).decode(errors="replace"))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())