#include <verilated.h>
#include "VCentralProcessingUnit.h" // Verilator 生成的头文件
#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <sstream>

using namespace std;

// 仿真全局变量
vluint64_t main_time = 0;

int main(int argc, char** argv) {
    std::ios::sync_with_stdio(true);
    
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);

    Verilated::commandArgs(argc, argv);
    VCentralProcessingUnit* dut = new VCentralProcessingUnit;

    // --- 1. 初始化复位 ---
    dut->reset = 1;
    dut->clock = 0;
    for(int i=0; i<10; i++) {
        dut->clock = !dut->clock;
        dut->eval();
    }
    dut->reset = 0;

    // --- 2. 对标 Scala: Loading Hex via PreloadBus ---
    string hexPath = "src/main/resources/program.hex";
    ifstream hexFile(hexPath);
    string line;
    uint32_t currentAddr = 0;

    cout << "--- Loading Hex into RAM via PreloadBus ---" << endl;

    while (getline(hexFile, line)) {
        if (line.empty()) continue;
        if (line[0] == '@') {
            currentAddr = stoul(line.substr(1), nullptr, 16);
        } else {
            stringstream ss(line);
            string b;
            vector<string> bytes;
            while (ss >> b) bytes.push_back(b);

            for (size_t i = 0; i + 3 < bytes.size(); i += 4) {
                uint32_t word = 0;
                for (int j = 0; j < 4; j++) {
                    word |= (uint32_t(stoul(bytes[i + j], nullptr, 16)) << (j * 8));
                }

                // 对标 dut.io.preload.xxx.poke
                dut->io_preload_en = 1;
                dut->io_preload_addr = currentAddr;
                dut->io_preload_data = word;

                // 对标 dut.clock.step(1)
                dut->clock = 0; dut->eval();
                dut->clock = 1; dut->eval();

                printf("[Preload] Load instr %08x at addr %08x\n", word, currentAddr);
                currentAddr += 4;
            }
        }
    }
    dut->io_preload_en = 0;
    cout << "Load complete." << endl;

    // --- 3. Simulation Loop ---
    int maxCycles = 250000;
    int cycles = 0;
    bool halted = false;

    cout << "--- Simulation Started ---" << endl;

    while (!halted && cycles < maxCycles) {
        // 对标 dut.io.isTerminated.peek()
        if (dut->io_isTerminated) {
            uint32_t result = dut->io_returnVal;
            printf("\n[HALT] Terminated at cycle %d\n", cycles);
            printf("Result (x10/a0): %u\n", result);
            halted = true;
        } else {
            // 对标 println(s"\nCycle $cycles...")
            printf("\nCycle %d...\n", cycles);
            
            // 对标 dut.clock.step(1)
            dut->clock = 0; dut->eval();
            dut->clock = 1; dut->eval();
            cycles++;
        }
    }

    if (!halted) cout << "\n[ERROR] Simulation Timed Out!" << endl;

    dut->final();
    delete dut;
    return 0;
}