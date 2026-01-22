int main() {
    int a[10], b[10];
    for(int i = 0; i < 10; i++) {
        a[i] = i;
        b[i] = 100 - i;
    }
    int res = 0;
    for(int i = 0; i < 10; i++) {
        res += a[i] * b[i];
    }
    return res;
}
