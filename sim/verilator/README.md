# Verilator Simulation

Firstly, make sure you have installed the [verilator](https://www.veripool.org/verilator/) and [cmake](https://cmake.org/install/). And you have the Verilog codes generated from Chisel project.

Then just run the following commands to generate `func_test_sim` program - the main program to run behavior simulation.

```bash
mkdir build
cd build && cmake ..
make -j
./func_test_sim
```

After `func_test_sim` program exits, it will produce a VCD waveform file `simx.vcd` as the simulation results. The easiest tool to view it is [GTKWave](https://gtkwave.sourceforge.net/).
