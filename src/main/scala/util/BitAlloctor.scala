package core.util

import chisel3._
import chisel3.util.DecoupledIO
import chisel3.util.PriorityEncoderOH
import chisel3.util.PopCount

class BitAlloctor(n: Int) extends Module {

  require(n > 0)

  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in = Flipped(DecoupledIO(UInt(n.W)))

    val out = DecoupledIO(UInt(n.W))

    val freeBits = Output(UInt(n.W))
  })
  val mask     = ~(0.U(n.W))
  val freeBits = RegInit(UInt(n.W), mask)
  val next     = PriorityEncoderOH(freeBits)

  when(io.flush) {
    freeBits := mask
  }.otherwise {
    freeBits := (freeBits | Mux(io.in.fire, io.in.bits, 0.U)) & Mux(io.out.fire, ~next, mask)
  }

  // IO
  io.in.ready := true.B

  io.out.bits  := next
  io.out.valid := next.orR

  io.freeBits := freeBits
}

object BitAlloctor {
  def apply(n: Int) = Module(new BitAlloctor(n))
}
