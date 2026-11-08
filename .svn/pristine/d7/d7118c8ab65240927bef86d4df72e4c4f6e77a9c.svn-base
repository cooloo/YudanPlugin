"""验证实际播放路径的最终字节码与宿主接口兼容。"""
import argparse
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
from dex_abi import check_dex, check_hosted_calls


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline")
    parser.add_argument("--host-apk")
    parser.add_argument("--android-jar", required=True)
    parser.add_argument("--dex", action="append", required=True)
    args = parser.parse_args()
    if args.host_apk:
        host, count = check_hosted_calls(args.host_apk, ("com/paopao/music/nowplaying/", "com/egogame/music/plugin/common/"), [args.android_jar])
        print(f"共享运行层最终 DEX 的 {count} 项宿主方法调用核对通过")
    elif args.baseline:
        host = json.loads(Path(args.baseline).read_text(encoding="utf-8"))["classes"]
    else:
        parser.error("必须指定最终宿主 APK 或成员基线")
    errors = []
    for path in args.dex:
        try:
            check_dex([path], host)
        except ValueError as failure:
            errors.append(str(failure))
    if errors:
        raise ValueError("\n".join(sorted(set(errors))))
    print("三个业务 DEX 与共享播放页的播放接口兼容核对通过")


if __name__ == "__main__":
    main()
