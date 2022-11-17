import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.Cat

import core.{Core, CoreParameter}
import core.util.BitAlloctor

// wrapper to fit test bench
class mycpu_top extends RawModule {
  implicit val p = new CoreParameter

  val clk     = IO(Input(Clock()))
  val resetn  = IO(Input(Bool()))
  val ext_int = IO(Input(UInt(6.W)))

  val inst_sram_en    = IO(Output(Bool()))
  val inst_sram_wen   = IO(Output(UInt(4.W)))
  val inst_sram_addr  = IO(Output(UInt(32.W)))
  val inst_sram_wdata = IO(Output(UInt(32.W)))
  val inst_sram_rdata = IO(Input(UInt(32.W)))

  val data_sram_en    = IO(Output(Bool()))
  val data_sram_wen   = IO(Output(UInt(4.W)))
  val data_sram_addr  = IO(Output(UInt(32.W)))
  val data_sram_wdata = IO(Output(UInt(32.W)))
  val data_sram_rdata = IO(Input(UInt(32.W)))

  val debug_wb_pc       = IO(Output(UInt(32.W)))
  val debug_wb_rf_wen   = IO(Output(UInt(4.W)))
  val debug_wb_rf_wnum  = IO(Output(UInt(5.W)))
  val debug_wb_rf_wdata = IO(Output(UInt(32.W)))

  withClockAndReset(clk, ~resetn) {
    val inst = Module(new Core).io
    inst_sram_en         := inst.inst_sram.en
    inst_sram_wen        := inst.inst_sram.wen
    inst_sram_addr       := inst.inst_sram.addr
    inst_sram_wdata      := inst.inst_sram.wdata
    inst.inst_sram.rdata := inst_sram_rdata

    data_sram_en         := inst.data_sram.en
    data_sram_wen        := inst.data_sram.wen
    data_sram_addr       := inst.data_sram.addr
    data_sram_wdata      := inst.data_sram.wdata
    inst.data_sram.rdata := data_sram_rdata

    debug_wb_pc       := inst.debug.pc
    debug_wb_rf_wen   := Cat(Seq.fill(4) { inst.debug.regWen(0) })
    debug_wb_rf_wnum  := inst.debug.regNum
    debug_wb_rf_wdata := inst.debug.regData
  }
}

import chisel3.stage.ChiselStage
import chisel3.stage.ChiselGeneratorAnnotation

object GetVerilog extends App {
  (new ChiselStage)
    .execute(
      Array("-X", "verilog", "--target-dir", "genrtl", "--target:fpga"),
      Seq(ChiselGeneratorAnnotation(() => new mycpu_top()))
    )
}
