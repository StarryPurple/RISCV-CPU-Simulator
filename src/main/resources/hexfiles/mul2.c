int main() {
    int a[100], b[100];
    for(int i = 0; i < 100; i++) {
        a[i] = i;
        b[i] = 100 ^ i;
    }
    int res = 0;
    for(int i = 0; i < 100; i++) {
        res += a[i] * b[i];
    }
    return res;
}
// RV32IM