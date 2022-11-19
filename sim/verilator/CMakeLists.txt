cmake_minimum_required(VERSION 3.12)

project(verify)

find_package(verilator HINTS $ENV{VERILATOR_ROOT})

add_executable(func_test_sim stub/func_test_sim.cpp)

get_filename_component(TRACE_REF_FILE "trace/func_test.txt" ABSOLUTE)
get_filename_component(INST_RAM_COE "coe/func_inst_ram.coe" ABSOLUTE)

verilate(func_test_sim
  TRACE_FST
  SOURCES
  tb/func_test_sram_tb.v
  rtl/confreg.v
  rtl/bridge_1x2.v
  rtl/sram/sram.v
  rtl/sram/data_ram.v
  rtl/sram/inst_ram.v
  ../genrtl/mycpu_top.v
  rtl/soc/soc_sram_top.v
  VERILATOR_ARGS -DTRACE_REF_FILE="${TRACE_REF_FILE}"
  VERILATOR_ARGS -DINST_RAM_COE="${INST_RAM_COE}"
  VERILATOR_ARGS -DRANDOMIZE_GARBAGE_ASSIGN
  VERILATOR_ARGS -DRANDOMIZE_INVALID_ASSIGN
  VERILATOR_ARGS -DRANDOMIZE_REG_INIT
  VERILATOR_ARGS -DRANDOMIZE_MEM_INIT
  VERILATOR_ARGS -Wno-WIDTH
)
