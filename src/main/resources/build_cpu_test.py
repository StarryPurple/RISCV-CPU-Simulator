import os
import subprocess
import sys

# --- 配置 ---
CC = "riscv64-unknown-elf-gcc"
OBJCOPY = "riscv64-unknown-elf-objcopy"
CFLAGS = "-march=rv32i -mabi=ilp32 -static -nostdlib -O2"
LNK_SCRIPT = "linker.ld"
CRT0 = "crt0.s"
SRC = "program.c"
OUTPUT_ELF = "program.elf"
OUTPUT_BIN = "program.bin"
OUTPUT_HEX = "./program.hex"

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error: {res.stderr}")
        sys.exit(1)

def main():
    # 1. 编译 ELF
    run_cmd(f"{CC} {CFLAGS} -T {LNK_SCRIPT} {CRT0} {SRC} -o {OUTPUT_ELF}")

    # 2. 提取 Binary (这是原始的字节流，不涉及字反转)
    run_cmd(f"{OBJCOPY} -O binary {OUTPUT_ELF} {OUTPUT_BIN}")

    if not os.path.exists(OUTPUT_BIN):
        return

    with open(OUTPUT_BIN, "rb") as f:
        bindata = f.read()

    final_hex = []
    # 起始地址（假设从 0 开始，如果有多个段，建议在 linker.ld 中紧凑排列）
    final_hex.append("@00000000")

    # 每 4 字节处理一次
    for i in range(0, len(bindata), 4):
        chunk = bindata[i:i+4]
        if len(chunk) == 4:
            # 直接按字节读取：chunk[0] 是最低字节，chunk[3] 是最高字节
            # 这会自动符合小端序在 Vec(4, UInt(8.W)) 中的映射
            # 结果格式：17 41 00 00
            bytes_str = " ".join(f"{b:02x}" for b in chunk)
            final_hex.append(bytes_str)

    # 3. 写入文件
    os.makedirs(os.path.dirname(OUTPUT_HEX), exist_ok=True)
    with open(OUTPUT_HEX, "w") as f:
        f.write("\n".join(final_hex))

    print(f"Generated Little-Endian Hex: {OUTPUT_HEX}")
    print("Preview:")
    for line in final_hex[:5]:
        print(f"  {line}")

if __name__ == "__main__":
    main()