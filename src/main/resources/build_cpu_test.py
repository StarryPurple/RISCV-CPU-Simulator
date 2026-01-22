import os
import subprocess
import sys

CC = "riscv64-unknown-elf-gcc"
OBJCOPY = "riscv64-unknown-elf-objcopy"
CFLAGS = "-march=rv32i -mabi=ilp32 -static -nostdlib -O0"
LNK_SCRIPT = "linker.ld"
CRT0 = "crt0.s"
HEADER = "header.h"
SRC = "program.c"
OUTPUT_ELF = "program.elf"
OUTPUT_BIN = "program.bin"
OUTPUT_HEX = "./program.hex"
OUTPUT_ELF_TXT = "program.elf.txt"

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error: {res.stderr}")
        sys.exit(1)

def main():
    run_cmd(f"{CC} {CFLAGS} -include {HEADER} -T {LNK_SCRIPT} {CRT0} {SRC} -o {OUTPUT_ELF}")
    
    run_cmd(f"riscv64-unknown-elf-objdump -d {OUTPUT_ELF} >{OUTPUT_ELF_TXT}")

    run_cmd(f"{OBJCOPY} -O binary {OUTPUT_ELF} {OUTPUT_BIN}")

    if not os.path.exists(OUTPUT_BIN):
        return

    with open(OUTPUT_BIN, "rb") as f:
        bindata = f.read()

    final_hex = []
    final_hex.append("@00000000")

    for i in range(0, len(bindata), 4):
        chunk = bindata[i:i+4]
        if len(chunk) == 4:
            bytes_str = " ".join(f"{b:02x}" for b in chunk)
            final_hex.append(bytes_str)

    os.makedirs(os.path.dirname(OUTPUT_HEX), exist_ok=True)
    with open(OUTPUT_HEX, "w") as f:
        f.write("\n".join(final_hex))

    print(f"Generated Little-Endian Hex: {OUTPUT_HEX}")
    print("Preview:")
    for line in final_hex[:5]:
        print(f"  {line}")

if __name__ == "__main__":
    main()