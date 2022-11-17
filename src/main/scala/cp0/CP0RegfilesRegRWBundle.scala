package core.cp0

import chisel3._

class CP0RegfilesRegRWBundle extends Bundle {
  val raddr = Input(UInt(5.W))
  val rdata = Output(UInt(32.W))
  val wen   = Input(Bool())
  val waddr = Input(UInt(5.W))
  val wdata = Input(UInt(32.W))
}
