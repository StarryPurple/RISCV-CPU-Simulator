#!/bin/bash

TEST_DIR="test"
TOP_MODULE="CentralProcessingUnit"
GEN_DIR="generated"
ELABORATE_CLASS="rvsim3.CPUElaborate"
SIM_MAIN=$TEST_DIR"/sim.cpp"
TARGET_EXE="VSim"
RESULT_LOG=$TEST_DIR"/simulation.log"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

cd .. # Enter project directory

echo -e "${GREEN}[1/3] Elaborating Chisel to Verilog...${NC}"

sbt "runMain ${ELABORATE_CLASS}"

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Chisel elaboration failed!${NC}"
    exit 1
fi

if [ ! -d "$GEN_DIR" ]; then
    echo -e "${RED}Error: Directory $GEN_DIR not found!${NC}"
    exit 1
fi

echo -e "${GREEN}[2/3] Compiling Verilog with Verilator...${NC}"

verilator --cc --exe --build -j $(nproc) \
    -O3 --trace \
    -I${GEN_DIR} \
    ${GEN_DIR}/${TOP_MODULE}.sv \
    ${SIM_MAIN} \
    --top-module ${TOP_MODULE} \
    -o ${TARGET_EXE}

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Verilator compilation failed!${NC}"
    exit 1
fi

echo -e "${GREEN}[3/3] Running Simulation...${NC}"

./obj_dir/${TARGET_EXE} 2>&1 | tee ${RESULT_LOG}

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Simulation Finished Successfully.${NC}"
else
    echo -e "${RED}Simulation Crashed or Terminated with Error.${NC}"
    exit 1
fi