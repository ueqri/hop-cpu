#include <verilated.h>          // Defines common routines
#include <iostream>             // Need std::cout
#include "Vfunc_test_sram_tb.h" // From Verilating "top.v"
#include "verilated_fst_c.h"

Vfunc_test_sram_tb* top; // Instantiation of model

uint64_t main_time = 0; // Current simulation time
// This is a 64-bit integer to reduce wrap over issues and
// allow modulus.  This is in units of the timeprecision
// used in Verilog (or from --timescale-override)

double sc_time_stamp() {                     // Called by $time in Verilog
    return main_time; // converts to double, to match
    // what SystemC does
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv); // Remember args
    Verilated::traceEverOn(true);

    top = new Vfunc_test_sram_tb; // Create model

    VerilatedFstC* tfp = new VerilatedFstC;
    top->trace(tfp, 99); // Trace 99 levels of hierarchy (or see below)
    if (argc > 1)
        tfp->open("simx.fst");
    // Do not instead make Vtop as a file-scope static
    // variable, as the "C++ static initialization order fiasco"
    // may cause a crash
    top->resetn = 0; // Set some inputs

    while (!Verilated::gotFinish()) {
        if (main_time > 64) {
            top->resetn = 1; // Deassert reset
        }

        top->clk = 1 - top->clk;

        top->eval(); // Evaluate model
        tfp->dump(main_time);
        main_time++; // Time passes...
    }

    top->final(); // Done simulating
    tfp->close();
    delete top;
}