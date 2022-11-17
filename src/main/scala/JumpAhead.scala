package core

import chisel3._
import chisel3.util._

class JumpAhead(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in  = Flipped(DecoupledIO(new MircoInstruction))
    val out = DecoupledIO(new MircoInstruction)

    val pcRedirect = ValidIO(UInt(32.W))
    val flushOut   = Output(Bool())
  })

  val jumpAddr = RegEnable(io.in.bits.jumpAddr, io.in.fire)

  val sNormal :: sWaitDelaySlot :: Nil = Enum(2)

  val state    = RegInit(sNormal)
  val stateNxt = WireInit(state)
  when(io.flush) {
    state := sNormal
  }.otherwise {
    state := stateNxt
  }

  switch(state) {
    is(sNormal) {
      when(io.out.fire && io.in.bits.isDircetJump && io.in.bits.execDelaySlot) {
        stateNxt := sWaitDelaySlot
      }
    }

    is(sWaitDelaySlot) {
      when(io.out.fire) {
        stateNxt := sNormal
      }
    }
  }
  val inSWaitDelaySlot = RegNext(stateNxt === sWaitDelaySlot, false.B)

  io.flushOut := io.pcRedirect.fire

  io.pcRedirect.bits  := Mux(inSWaitDelaySlot, jumpAddr, io.in.bits.jumpAddr)
  io.pcRedirect.valid := io.out.fire && ((~inSWaitDelaySlot && io.in.bits.isDircetJump && ~io.in.bits.execDelaySlot) || inSWaitDelaySlot)

  io.in                    <> io.out
  io.in.ready              := io.out.ready && ~io.flush
  io.out.bits.isDircetJump := DontCare
  io.out.bits.jumpAddr     := DontCare
}
