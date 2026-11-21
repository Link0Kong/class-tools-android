#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""用 OPPO 官方 OPPOSign.jar 计算 release APK 的 OPPOSign（无 GUI）。

用法：
  python scripts/oppo_sign.py --apk app/build/outputs/apk/release/app-release.apk \
      [--jar C:\\...\\OPPOSign\\bin\\OPPOSign.jar]

退出码：0 成功（stdout 最后一行 `OPPOSIGN <32位大写HEX>`）；非 0 失败。
Jar 路径优先级：--jar > 环境变量 OPPOSIGN_JAR > 本机默认路径。
"""

import argparse
import os
import shutil
import subprocess
import sys

DEFAULT_JAR = r"C:\Users\Ko_Li\Downloads\cc227743e4c29d116c22d545fe486c29\OPPOSign\bin\OPPOSign.jar"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def find_java_tool(name):
    java_home = os.environ.get("JAVA_HOME", "")
    if java_home:
        candidate = os.path.join(java_home, "bin", name + (".exe" if os.name == "nt" else ""))
        if os.path.isfile(candidate):
            return candidate
    found = shutil.which(name)
    if found:
        return found
    raise RuntimeError("找不到 %s：请设置 JAVA_HOME 或加入 PATH" % name)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--jar", default=os.environ.get("OPPOSIGN_JAR", DEFAULT_JAR))
    args = ap.parse_args(argv)

    apk = os.path.abspath(args.apk)
    jar = os.path.abspath(args.jar)
    if not os.path.isfile(apk):
        print("APK_NOT_FOUND %s" % apk, file=sys.stderr)
        return 2
    if not os.path.isfile(jar):
        print("OPPOSIGN_JAR_NOT_FOUND %s" % jar, file=sys.stderr)
        return 2

    classes = os.path.join(ROOT, "build", "opposign", "classes")
    os.makedirs(classes, exist_ok=True)
    javac = find_java_tool("javac")
    java = find_java_tool("java")
    source = os.path.join(HERE, "OppoSignCli.java")

    subprocess.run(
        [javac, "-encoding", "UTF-8", "-cp", jar, "-d", classes, source],
        check=True,
    )
    cp = jar + os.pathsep + classes
    proc = subprocess.run(
        [java, "-cp", cp, "OppoSignCli", apk],
        check=True,
        capture_output=True,
        text=True,
    )
    value = (proc.stdout or "").strip().splitlines()[-1].strip() if proc.stdout else ""
    if len(value) != 32 or any(c not in "0123456789ABCDEF" for c in value):
        print("OPPOSIGN_INVALID %r" % value, file=sys.stderr)
        return 3

    out_dir = os.path.join(ROOT, "app", "build", "outputs")
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "opposign.txt"), "w", encoding="utf-8") as f:
        f.write(value + "\n")
    with open(os.path.join(out_dir, "opposign.json"), "w", encoding="utf-8") as f:
        f.write('{"apk":"%s","oppoSign":"%s"}\n' % (os.path.basename(apk), value))
    print("OPPOSIGN %s" % value)
    return 0


if __name__ == "__main__":
    sys.exit(main())
