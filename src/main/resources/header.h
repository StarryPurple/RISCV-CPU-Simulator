#ifndef HEADER_H
#define HEADER_H

int __mulsi3(int a, int b) {
    int res = 0;
    while (b > 0) {
        if (b & 1) res += a;
        a <<= 1;
        b >>= 1;
    }
    return res;
}

static void __udivmodsi4(unsigned int n, unsigned int d, unsigned int* q, unsigned int* r) {
    unsigned int quot = 0, rem = 0;
    for (int i = 31; i >= 0; i--) {
        rem <<= 1;
        rem |= (n >> i) & 1;
        if (rem >= d) {
            rem -= d;
            quot |= (1 << i);
        }
    }
    if (q) *q = quot;
    if (r) *r = rem;
}

unsigned int __udivsi3(unsigned int n, unsigned int d) {
    unsigned int q;
    __udivmodsi4(n, d, &q, 0);
    return q;
}

unsigned int __umodsi3(unsigned int n, unsigned int d) {
    unsigned int r;
    __udivmodsi4(n, d, 0, &r);
    return r;
}

int __divsi3(int n, int d) {
    int sign = (n < 0) ^ (d < 0);
    unsigned int un = (n < 0) ? -n : n;
    unsigned int ud = (d < 0) ? -d : d;
    unsigned int q = __udivsi3(un, ud);
    return sign ? -q : q;
}

int __modsi3(int n, int d) {
    int sign = (n < 0);
    unsigned int un = (n < 0) ? -n : n;
    unsigned int ud = (d < 0) ? -d : d;
    unsigned int r = __umodsi3(un, ud);
    return sign ? -r : r;
}

#endif