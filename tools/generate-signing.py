#!/usr/bin/env python3
"""生成本地插件签名，密码不出现在日志或命令行；默认创建开发者自签证书。"""
import argparse
import hashlib
import secrets
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", default=".local/signing")
    parser.add_argument("--alias", default="developer")
    parser.add_argument("--name", default="YudanPlugin Developer")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    directory = (root / args.directory).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    store = directory / "plugin-signing.p12"
    if store.exists():
        raise ValueError("签名密钥已存在，禁止覆盖")
    password = secrets.token_urlsafe(48)
    password_file = directory / "password.txt"
    password_file.write_text(password + "\n", encoding="utf-8")
    try:
        subprocess.run(["keytool", "-genkeypair", "-storetype", "PKCS12", "-keystore", str(store),
                        "-storepass:file", str(password_file), "-keypass:file", str(password_file),
                        "-alias", args.alias, "-keyalg", "RSA", "-keysize", "3072", "-validity", "36500",
                        "-dname", f"CN={args.name},O={args.name}"], check=True, capture_output=True)
        certificate = directory / "certificate.der"
        subprocess.run(["keytool", "-exportcert", "-keystore", str(store), "-storepass:file", str(password_file),
                        "-alias", args.alias, "-file", str(certificate)], check=True, capture_output=True)
        config = (f"storeFile={store.as_posix()}\nkeyAlias={args.alias}\n"
                  "storePasswordEnv=YD_PLUGIN_STORE_PASSWORD\nkeyPasswordEnv=YD_PLUGIN_KEY_PASSWORD\n"
                  "storePasswordFile=password.txt\nkeyPasswordFile=password.txt\nsignatureAlgorithm=SHA256withRSA\n")
        (directory / "plugin-signing.properties").write_text(config, encoding="utf-8")
        print("证书 SHA-256：" + hashlib.sha256(certificate.read_bytes()).hexdigest())
        print("签名配置：" + str(directory / "plugin-signing.properties"))
    except Exception:
        raise RuntimeError("生成签名失败，请核对 JDK 和本地目录；不输出包含本地材料的子进程日志") from None


if __name__ == "__main__":
    main()
