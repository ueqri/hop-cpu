package core.lsu

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.SRAMBundle
import core.util.PrintDebugger
import core.FixedMemMap

class SramCtrl(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val inLoad  = Flipped(DecoupledIO(new MemLoadBundle))
    val outLoad = DecoupledIO(new MemLoadBundle)

    val inStore = Flipped(DecoupledIO(new MemoryStoreBundle))

    val dmem = new SRAMBundle()
  })
  // prio store
  val selLoad  = ~io.inStore.valid
  val selStore = ~selLoad

  val sIdle :: sLoadOut :: Nil = Enum(2)

  val state    = RegInit(sIdle)
  val stateNxt = WireInit(state)
  when(io.flush) {
    state := sIdle
  }.otherwise {
    state := stateNxt
  }

  switch(state) {
    is(sIdle) {
      when(io.inLoad.valid && ~io.inStore.valid) {
        stateNxt := sLoadOut
      }
    }

    is(sLoadOut) {
      when(io.outLoad.ready) {
        when(~io.inLoad.fire) {
          stateNxt := sIdle
        }
      }
    }
  }

  val inReady = state === sIdle || io.outLoad.ready

  val loadInfo = RegEnable(io.inLoad.bits, io.inLoad.fire)

  // io
  io.inLoad.ready  := inReady && selLoad
  io.inStore.ready := inReady && selStore

  io.outLoad.valid     := state === sLoadOut
  io.outLoad.bits      := loadInfo
  io.outLoad.bits.data := io.dmem.rdata

  io.dmem.en    := io.inLoad.fire || io.inStore.fire
  io.dmem.wen   := Mux(io.inStore.fire, io.inStore.bits.strb, 0.U)
  io.dmem.addr  := FixedMemMap(Mux(io.inStore.fire, io.inStore.bits.addr, io.inLoad.bits.addr))
  io.dmem.wdata := io.inStore.bits.data
}
