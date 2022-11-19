# Hop: HUST Out-of-order Processor

![hop-cpu](./doc/hop-cpu.svg)

## Features

- Adopted modern CPU designs from [BOOM](https://github.com/riscv-boom/riscv-boom) and [RSD](https://github.com/rsd-devel/rsd), and re-implemented some of their features in Chisel (e.g., [Dependencies Matrix for Renaming](https://docs.boom-core.org/en/latest/sections/rename-stage.html), [Multi-Port RAMs](https://tomverbeure.github.io/2019/08/03/Multiport-Memories.html))
- Exploited Chisel features & [Rocket Chip](https://github.com/chipsalliance/rocket-chip) styles to agilely develop CPU
- Implemented abstractions with Chisel to easily configure hyperparameter and upgrade modules (like caches)
- Used [Gem5 O3PipeView](https://www.gem5.org/documentation/general_docs/cpu_models/visualization/) and [Konata](https://github.com/shioyadan/Konata) to visualize pipeline operations during simulation

## Build and Run

### Prerequisite

- [Chisel3](https://www.chisel-lang.org/): See the official [setup instructions](https://github.com/chipsalliance/chisel3/blob/master/SETUP.md) for how to set up your Scala environment to build Chisel locally.
- [Vivado](https://www.xilinx.com/products/design-tools/vivado.html): 2019.2 or above for RTL simulation (to verify and debug) and synthesis (to program on FPGA board).
- (Optional) [Verilator](https://www.veripool.org/verilator/): tiny simulator used to do RTL verification without bulky Vivado.
- (Optional) [Scala-Metals](https://scalameta.org/metals/docs/editors/vscode/): if you prefer VS Code as your editor, strongly recommend you to install this plugin to have a great coding experience.

### Get Verilog

Since the Verilog generation is defined as the entrypoint of the Scala program, simply running the following command can get Verilog codes with the power of Chisel:
```bash
sbt run
```

The Verilog codes will be on `genrtl` directory. For other RTL target (like VHDL), simply shift to your preferable target in `src/main/scala/mycpu_top.scala`.

### Test

We already provides some tests covering many util modules we wrote, see them in `src/test/scala` directory. To run the unit test,
```bash
sbt test # Run all unit tests at once
sbt "testOnly core.util.MultiPortRamSpec" # Just run a single test with spec name
```

For more tutorial about how to write or run tests, please refer to [ucb-bar/chiseltest](https://github.com/ucb-bar/chiseltest).

## Simulation and Visualization

### Behavior Simulation

To run behavior simulation with Vivado tool (our main approach), please refer to `sim/vivado` directory.

If you want to try much light verilator simulation, see `sim/verilator` for all environment needed (probably has flaws in sim results).

### Pipeline Visualization

We novelly uses O3 Pipeline Viewer (a powerful tool in gem5 ecosystem) to view inst pipeline from the dumped simulation log. It helps us distinguish the performance issues when constructing the CPU, and work as a lifesaver for debugging!

One of our vis results as below - it is helpful and fancy!

[pic]

## Design Details

In our submitted version, we finally covers MIPS32 (based on Release 1, [57 insts in total](https://github.com/ueqri/hop-cpu/tree/main/src/main/scala/Instructions.scala)) ISA, achieves 100 MHz with Vivado 2021.2, and passes all function tests (both mandatory and advanced). There is some steps we haven't finished because of the limited time, and also some interesting ideas, see [Follow-ups](#follow-ups).

Please refer to [doc/README.md](https://github.com/ueqri/hop-cpu/tree/main/doc) for the architecture, and [doc/lesson-story.md](https://github.com/ueqri/hop-cpu/tree/main/doc/lesson-story.md) for the lessons we learned and interesting stories during the exploration and development.

## Acknowledgements

This project is a team work collaborated with @Jomit626, @Kepontry, and @nothatDinger, within 3 months.

If you are interested in this project (e.g., wanting a discussion on the technical details, preparing for the Computer Organization course project, or just desiring to build a geek CPU core for fun), please feel free to drop me an email to start a chat.

If you plan to attend the *Loongson Cup Student CPU Design Competition* and you're a HUST undergrad, please contact Prof. [Zhihu Tan](http://faculty.hust.edu.cn/tanzhihu/en/index.htm) for more information.

## Follow-ups

[ ] Organize the documents & tutorials as a project for educational purpose for HUST new CSer
[ ] Fix bug of AXI controller to replace the current SRAM design
[ ] Extend issue windows to support 2 (or above) inst issues
[ ] Implement caches for better performance, and TLB MMU for OS support
[ ] Optimize retire stage (still a lot of room) and branch predictor
[ ] Run RTOS (first step) and Linux (next step)

## Reference

[Getting Started with Chisel (CS 250, UCB)](https://inst.eecs.berkeley.edu/~cs250/sp16/handouts/chisel-getting-started.pdf)