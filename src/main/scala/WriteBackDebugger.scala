package core

import chisel3._
import chisel3.util._

import core.rob.CommitBundle
import core.util.PrintDebugger

class WriteBackDebugger(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val commit = Flipped(ValidIO(new CommitBundle()))
    val debug  = Output(new DebugBundle)
  })

  def commitToDebug(x: CommitBundle, fire: Bool): DebugBundle = {
    val d = Wire(new DebugBundle)

    d.pc      := x.ctrl.pc
    d.regData := x.result.data
    d.regNum  := x.ctrl.regDstLogic
    d.regWen  := x.ctrl.regDstWrite && fire

    d
  }

  io.debug := commitToDebug(io.commit.bits, io.commit.fire)
}
