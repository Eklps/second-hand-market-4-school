import os

path = r"f:\我的一些东西\后端学习\2hand\3. 秒杀系统模块.md"

with open(path, "r", encoding="utf-8") as f:
    text = f.read()

if text.startswith("\ufeff"):
    text = text[1:]

try:
    original_bytes = text.encode("gbk")
    original_text = original_bytes.decode("utf-8")
    with open(path, "w", encoding="utf-8") as f:
        f.write(original_text)
    print("Recovery successful!")
except Exception as e:
    print("Recovery failed again:", e)
