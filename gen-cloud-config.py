#!/usr/bin/env python3
"""Generate cloud-config.enc from DAHUA_USE_PASS. Never prints the password."""
import os, json, sys, secrets
from pathlib import Path

def key_bytes():
    a = bytes([0x4A, 0x68, 0x44, 0x76, 0x41, 0x70, 0x50, 0x77, 0x4B, 0x39, 0x6D, 0x52, 0x73, 0x58, 0x71, 0x2B])
    b = bytes([0x7C, 0x31, 0xE9, 0xA4, 0x55, 0x0B, 0xD2, 0x68, 0x3F, 0x9A, 0x14, 0xC7, 0xE0, 0x5D, 0x82, 0x6F])
    return bytes(x ^ y for x, y in zip(a, b))

def encrypt(plain: bytes) -> bytes:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    iv = secrets.token_bytes(12)
    ct = AESGCM(key_bytes()).encrypt(iv, plain, None)
    return iv + ct

def main():
    pw = os.environ.get("DAHUA_USE_PASS", "")
    if not pw:
        print("DAHUA_USE_PASS missing", file=sys.stderr); sys.exit(1)
    sync = os.environ.get("DAHUA_SYNC_TOKEN", "")
    obj = {
        "pub_host": "222.129.161.254", "pub_port": 28554,
        "lan_host": "192.168.9.45", "lan_port": 554,
        "user": "use", "pass": pw,
        "updatedAt": int(__import__("time").time() * 1000),
    }
    if sync:
        obj["sync_token"] = sync
    blob = encrypt(json.dumps(obj, ensure_ascii=False).encode())
    Path("cloud-config.enc").write_bytes(blob)
    print(f"wrote cloud-config.enc ({len(blob)} bytes, pass_len={len(pw)}, sync={'yes' if sync else 'no'})")

if __name__ == "__main__":
    main()
