# crt0.s
.section .text
.globl _start

_start:
    lui sp, 0x20
    jal ra, main
    sb x0, -1(x0)
