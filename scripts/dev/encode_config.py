#!/usr/bin/env python3
"""
将 API 配置项编码为 Base64 字符串（仅作本地/文档辅助）。
应用不再内置任何 API Key 或服务商地址，请在应用内「配置」页填写。
"""
import argparse
import base64


def encode_to_base64(text: str) -> str:
    return base64.b64encode(text.encode("utf-8")).decode("utf-8")


def print_encoded_config(base_url, api_key, model, temperature, max_tokens):
    print("=" * 60)
    print("Base64-encoded values (for your own notes / docs only):")
    print("=" * 60)
    print(f'    ENCODED_BASE_URL = "{encode_to_base64(base_url)}"')
    print(f'    ENCODED_API_KEY = "{encode_to_base64(api_key)}"')
    print(f'    ENCODED_MODEL = "{encode_to_base64(model)}"')
    print(f"    TEMPERATURE = {temperature}")
    print(f"    MAX_TOKENS = {max_tokens}")
    print("\nThe app does not ship built-in credentials; configure API in the app.")


def main():
    parser = argparse.ArgumentParser(description="Encode API configuration to Base64 (reference only)")
    parser.add_argument("--url", required=True, help="API Base URL")
    parser.add_argument("--key", required=True, help="API Key")
    parser.add_argument("--model", default="gpt-3.5-turbo", help="Model name")
    parser.add_argument("--temperature", type=float, default=0.7, help="Temperature (0-2)")
    parser.add_argument("--max_tokens", type=int, default=4096, help="Max tokens")

    args = parser.parse_args()

    print_encoded_config(
        base_url=args.url,
        api_key=args.key,
        model=args.model,
        temperature=args.temperature,
        max_tokens=args.max_tokens,
    )


if __name__ == "__main__":
    main()
