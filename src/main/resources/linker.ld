SECTIONS
{
    /* 从 0 地址开始 */
    . = 0x00000000;

    /* 代码段 */
    .text : {
        *(.text)
        *(.text.*)
    }

    /* 只读数据段 */
    .rodata : {
        *(.rodata)
        *(.rodata.*)
    }

    /* 数据段 (紧跟在 text 后面) */
    .data : {
        *(.data)
        *(.data.*)
    }

    /* BSS 段 (未初始化变量) */
    .bss : {
        *(.bss)
        *(.common)
    }

    /* 定义栈顶：假设你的仿真内存有 16KB (4096 * 4字节) */
    _stack_top = 0x00004000;
}