#!/usr/bin/env python3
"""
Verify NetEase Cloud Music weapi encryption by comparing with Android output.

Usage:
    pip install pycryptodome
    python verify_nc_encrypt.py

Run this script AND the Android app with the SAME secondKey.
Compare the firstAesBase64, params, and encSecKey values.
"""

import json
import base64
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

# ── Fixed constants ──────────────────────────────────────────────────────
FIRST_KEY = "0CoJUm6Qyw8W8jud"
IV = "0102030405060708"
RSA_EXPONENT = "010001"
RSA_MODULUS = (
    "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b7"
    "25152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e"
    "0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cc"
    "e10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece04"
    "62db0a22b8e7"
)

# Use a FIXED second key for comparison (copy this from Android logcat output)
SECOND_KEY = "Fc6Z5gx18Sd8HPhq"  # <-- REPLACE with value from Android log

def aes_encrypt(text: str, key: str) -> str:
    """AES-128-CBC with PKCS7 padding, returns Base64 string."""
    cipher = AES.new(key.encode('utf-8'), AES.MODE_CBC, IV.encode('utf-8'))
    padded = pad(text.encode('utf-8'), AES.block_size)
    encrypted = cipher.encrypt(padded)
    return base64.b64encode(encrypted).decode('utf-8')

def rsa_encrypt(key_str: str) -> str:
    """RSA encrypt the reversed key string."""
    reversed_key = key_str[::-1]
    hex_val = reversed_key.encode('utf-8').hex()
    # Python's built-in pow with mod
    rs = pow(int(hex_val, 16), int(RSA_EXPONENT, 16), int(RSA_MODULUS, 16))
    return format(rs, 'x').zfill(256)

def main():
    # ── Build the SAME JSON body as the Android app ──────────────────────
    # Copy the exact jsonText from Android logcat:
    #   [NeteaseEncryptor] jsonText={"hlpretag":"<span class=\"s-fc7\">",...}
    import json as j
    body = j.dumps({
        "hlpretag": '<span class="s-fc7">',
        "hlposttag": '</span>',
        "s": "baby Justin Bieber",
        "type": 1,
        "offset": 0,
        "total": True,
        "limit": 30,
        "csrf_token": "",
    }, separators=(',', ':'), ensure_ascii=False)

    print(f"=== JSON body ===")
    print(f"  {body}")
    print()

    # ── First AES (fixed key) ───────────────────────────────────────────
    first_b64 = aes_encrypt(body, FIRST_KEY)
    print(f"=== firstAesBase64 ===")
    print(f"  {first_b64}")
    print()

    # ── Second AES (random/second key) ──────────────────────────────────
    params = aes_encrypt(first_b64, SECOND_KEY)
    print(f"=== params (first 60) ===")
    print(f"  {params[:60]}...")
    print()

    # ── RSA encrypt reversed key ────────────────────────────────────────
    enc_sec_key = rsa_encrypt(SECOND_KEY)
    print(f"=== encSecKey (first 60) ===")
    print(f"  {enc_sec_key[:60]}...")
    print(f"  length={len(enc_sec_key)}")
    print()

    print("─" * 60)
    print("Compare these values with Android logcat output:")
    print("  adb logcat -s NeteaseEncryptor")
    print()
    print("The firstAesBase64 and encSecKey should match EXACTLY.")
    print("The params will differ because of different secondKey —")
    print("  edit SECOND_KEY above to match the Android secondKey value.")

if __name__ == "__main__":
    main()
