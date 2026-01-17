int main() {
    register int a = 1; // F(1)
    register int b = 1; // F(2)
    register int c;

    c = a + b; // F(3) = 2
    a = b + c; // F(4) = 3
    b = c + a; // F(5) = 5
    c = a + b; // F(6) = 8
    a = b + c; // F(7) = 13
    b = c + a; // F(8) = 21

    return b;
}