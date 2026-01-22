int main() {
    int a = 0, b = 1, tmp = 0;
    for(int i = 0; i < 100; i++) {
        tmp = a + b;
        if(tmp > 10000) {
            tmp -= 10000;
        }
        a = b;
        b = tmp;
    }
    return a; // 5075
}
