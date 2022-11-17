package core.cp0

import chisel3._
import chisel3.util._

import core.CoreParameter

class IntArbiter(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val intPending = Input(UInt(8.W))
    val intMask    = Input(UInt(8.W))
    val intEnable  = Input(Bool())
    val EXL        = Input(Bool())

    val int = ValidIO(UInt(3.W))
  })

  val intOk = io.intPending & io.intMask

  val intOKIndex = PriorityEncoder(intOk)

  io.int.bits  := intOKIndex
  io.int.valid := intOk.orR & io.intEnable & ~io.EXL
}
