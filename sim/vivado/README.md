# Vivado Helper scripts

## Behavior Simulation

`behav_sim.tcl` helps debug the CPU.

``` bash
vivado -sources behav_sim.tcl ../func_test_v0.01/soc_sram_func/run_vivado/mycpu_prj1/mycpu.xpr
```

This command will open Vivado GUI (replace xpr file with yours first), and then launch the simulation.
If there are further changes made to Chisel codes, please regenerate the Verilog first using `sbt run GetVerilog`, and enter `resim` in Vivado TCL console just to recompile sources & restart simulation.

One might add `-nolog -nojournal` options to avoid vivado generate to many logs.

## Note

Please replace the xpr, constraints, SoC files to yours, this helper scripts only aims to easily maintain the "Chisel -> Vivado Simulation" workflow.

if you need our Vivado project (which only targets our school FPGA board) for reference, feel free to drop me a mail and I will organize it to you.

Or, you can see the verilator simulation `sim/verilator` for a more general solution.