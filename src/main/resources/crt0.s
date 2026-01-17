# crt0.s
.section .text
.globl _start

_start:
    la sp, _stack_top
    call main

loop:
    j loop
