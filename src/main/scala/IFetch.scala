package core

import chisel3._
import chisel3.util.DecoupledIO
import chisel3.util.ValidIO
import chisel3.util.RegEnable

import core.util.PrintDebugger

class IFetchOutputBundle extends Bundle {
  val pc          = UInt(32.W)
  val instruction = UInt(32.W)
}

class IFetch(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val imem = new SRAMBundle()

    val out = DecoupledIO(new IFetchOutputBundle)

    val pcRedirect = Flipped(ValidIO(UInt(32.W)))
  })

  val streamId = RegInit(0.U(2.W)) // require 2 bits to avoid error when 2 continuous pc redirection
  val pc       = RegInit(p.PCInitValue.U(32.W))

  val insPC       = RegEnable(pc, io.out.ready)
  val insStreamId = RegEnable(streamId, 1.U, io.out.ready)

  when(io.out.ready) {
    pc := pc + 4.U
  }

  when(io.pcRedirect.fire) {
    pc       := io.pcRedirect.bits
    streamId := streamId +& 1.U
  }

  // IO
  io.imem.en    := io.out.ready
  io.imem.wen   := 0.U
  io.imem.addr  := FixedMemMap(pc)
  io.imem.wdata := DontCare

  io.out.bits             := DontCare
  io.out.bits.pc          := insPC
  io.out.bits.instruction := io.imem.rdata
  io.out.valid            := streamId === insStreamId

  // debug
  if (p.DebugPipeViewLog) {
    val prevPC       = RegNext(pc, init = (p.PCInitValue + 1).U)
    val prevStreamId = RegNext(streamId, init = 0.U)
    when((pc =/= prevPC || prevStreamId =/= streamId) && ~io.pcRedirect.fire) { // start fetching
      dprintln(p"PipeView:fetch:0x${Hexadecimal(pc)}")
    }
    when(io.pcRedirect.fire && io.out.valid) {
      dprintln(p"PipeView:flush:0x${Hexadecimal(io.out.bits.pc)}")
    }
  }
}
