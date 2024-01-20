/******************************************************************************
* File:             sim_main.cpp
*
# Author:           Hyunwoo Oh, Computer Architecture Lab., SEOULTECH
* Created:          01/20/24 
* Description:      Simple cpp code for Verilator simulation
*****************************************************************************/

#include <iostream>
#include <cstdlib>
#include <ctime>
#include "verilated.h"
#include "verilated_vcd_c.h"
#include "VDLSorter.h"

#define LOG_E 6
#define LOG_P 2

#define STREAM_CNT (1<<(LOG_E-LOG_P))

#define SIM_TICK() \
    top->eval(); \
    tfp->dump(Verilated::time())
#define SIM_TIME(t) \
    Verilated::timeInc(t)

int main(int argc, char** argv, char** env) {
    // Prevent unused variable warnings
    if (false && argc && argv && env) {}

    // Construct a VerilatedContext to hold simulation time, etc.
    VerilatedContext* contextp = new VerilatedContext;

    // Create logs/ directory in case we have traces to put under it
    Verilated::mkdir("logs");
    // Set debug level, 0 is off, 9 is highest presently used
    // May be overridden by commandArgs argument parsing
    Verilated::debug(0);

    // Randomization reset policy
    // May be overridden by commandArgs argument parsing
    Verilated::randReset(2);

    // Verilator must compute traced signals
    Verilated::traceEverOn(true);

    // Construct the Verilated model, from Vtop.h generated from Verilating "top.v"
    Verilated::commandArgs(argc, argv);

    // Construct the Verilated model, from Vtop.h generated from Verilating "top.v"
    VDLSorter* top = new VDLSorter{contextp};

    // Construct the VCD dump object
    VerilatedVcdC* tfp = new VerilatedVcdC;
    top->trace(tfp, 99);
    tfp->open("logs/wave.vcd");

    srand((uint32_t)time(NULL));

    //// Start simulation
    std::cout << "================================" << std::endl;
    std::cout << "======= Start Simulation =======" << std::endl;
    std::cout << "================================" << std::endl;

    // Input signal initialization and resetting design
    std::cout << "Reseting top module...." << std::endl;
    top->clock = 1; top->reset = 0;
    top->io_ctrl_we     = 0;
    top->io_ctrl_weEnd  = 0;
    top->io_ctrl_clear  = 0;
    top->io_ctrl_tagRe  = 1;
    top->io_ctrl_dataRe  = 1;
    SIM_TICK(); SIM_TIME(1);
    top->reset = 1;
    SIM_TICK(); SIM_TIME(9);
    top->clock = 0; top->reset = 0;
    SIM_TICK(); SIM_TIME(10);

    clock_t elapsed_time = clock();
    // Check for worst case for 8 times
    for (int i = 0; i < 8; ++i) {
        for (int j = 0; j < STREAM_CNT; ++j) {
            top->clock = !top->clock;
            SIM_TICK();
            // between clock to clock
            
            top->io_ctrl_we = 1;
            top->io_data_iData_0 = 0x00000000;
            top->io_data_iData_1 = 0x11111111;
            top->io_data_iData_2 = 0xFFFFFFFF;
            top->io_data_iData_3 = 0xEEEEEEEE;
            
            SIM_TIME(10);
            top->clock = !top->clock;
            SIM_TICK(); SIM_TIME(10);
        }
    }
    // Check for random numbers for 8 times
    for (int i = 0; i < 8; ++i) {
        for (int j = 0; j < STREAM_CNT; ++j) {
            top->clock = !top->clock;
            SIM_TICK();
            // between clock to clock
            
            top->io_ctrl_we = 1;
            top->io_data_iData_0    = rand() * (0xFFFFFFFF/RAND_MAX);
            top->io_data_iData_1    = rand() * (0xFFFFFFFF/RAND_MAX);
            top->io_data_iData_2    = rand() * (0xFFFFFFFF/RAND_MAX);
            top->io_data_iData_3    = rand() * (0xFFFFFFFF/RAND_MAX);

            SIM_TIME(10);
            top->clock = !top->clock;
            SIM_TICK(); SIM_TIME(10);
        }
    }
    /*
    while (!Verilated::gotFinish()) {
        // Nonblocking assignments
        top->clk = ~top->clk;
        SIM_TICK(); SIM_TIME(5);
        // Blocking assignments

        // Read outputs
        VL_PRINTF("[%" PRId64 "] clk=%x reset_n=%x \n",
                  Verilated::time(), top->clk, top->reset_n);
    }
    */

    //// Simulation ends
    elapsed_time = clock() - elapsed_time;

    std::cout << "================================" << std::endl;
    std::cout << "======= Simulation Ended =======" << std::endl;
    std::cout << "================================" << std::endl;
    std::cout << "Elapsed time: " << (double)elapsed_time / (CLOCKS_PER_SEC<<2) << std::endl;

    // Final model cleanup
    top->final();

    //// Coverage analysis
    //contextp->coveragep()->write("logs/coverage.dat");

    delete top;
    delete contextp;
    return 0;
}
