package core.rename

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.MircoInstruction
import core.rob.CommitBundle
import core.util.BitAlloctor
import core.util.PrintDebugger
import core.util.Stage

class IRename(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in  = Flipped(DecoupledIO(MircoInstruction()))
    val out = DecoupledIO(MircoInstruction())

    val commitHiloReg = Flipped(ValidIO(UInt(p.PhyHiLoRegBits.W)))
    val freeHiloReg   = Flipped(ValidIO(UInt(p.PhyHiLoRegBits.W)))

    val commitPhyReg = Flipped(ValidIO(new CommitPhyRegBundle))
    val freePhyReg   = Flipped(ValidIO(UInt(p.PhyRegBits.W)))

    val branchRestore = Flipped(ValidIO(UInt(p.BranchIndexNum.W)))

    val exceptionRestore = Input(Bool())
  })

  val phyRegManager  = Module(new PhyRegManager())
  val hiLoRegManager = Module(new HiLoRegManager())

  // Rename Logic
  val stage = Stage(io.in, io.flush)
  val ins   = stage.bits
  val out   = Wire(chiselTypeOf(ins))
  out := ins

  phyRegManager.io.log2phyMap(0).log := ins.reg0(4, 0)
  phyRegManager.io.log2phyMap(1).log := ins.reg1(4, 0)

  out.reg0 := phyRegManager.io.log2phyMap(0).phy
  out.reg1 := phyRegManager.io.log2phyMap(1).phy

  val allocPhy  = stage.valid && ins.regDstWrite && ~io.flush
  val allocHiLo = stage.valid && ins.writeHiLo && ~io.flush
  val renameDone = (allocPhy && phyRegManager.io.phyAlloc.ready) ||
    (allocHiLo && hiLoRegManager.io.phyAlloc.ready) ||
    (~allocPhy && ~allocHiLo)

  phyRegManager.io.phyAlloc.valid := allocPhy && io.out.ready
  phyRegManager.io.phyAlloc.bits  := ins.regDstLogic

  hiLoRegManager.io.phyAlloc.valid := allocHiLo && io.out.ready

  // no dedicated regwrite singal is sent to exec unit
  // phyReg 0 is used to tell exec unit not to write phy reg
  out.regDst     := Mux(ins.regDstWrite, phyRegManager.io.phy, 0.U)
  out.regDstOld  := phyRegManager.io.phyOld
  out.hiLoReg    := Mux(ins.writeHiLo, hiLoRegManager.io.phy, hiLoRegManager.io.curPhy)
  out.hiLoRegOld := hiLoRegManager.io.phyOld

  phyRegManager.io.commitReg   <> io.commitPhyReg
  phyRegManager.io.freePhyReg  <> io.freePhyReg
  hiLoRegManager.io.commitReg  <> io.commitHiloReg
  hiLoRegManager.io.freePhyReg <> io.freeHiloReg

  val branchBackup        = Wire(chiselTypeOf(phyRegManager.io.branchBackup))
  val pendingBranchbackup = RegInit(false.B)
  when(io.flush) {
    pendingBranchbackup := false.B
  }.elsewhen(io.out.fire) {
    pendingBranchbackup := ins.isBranch
  }
  branchBackup.valid := pendingBranchbackup && io.out.fire // backup state on delayslot inst
  branchBackup.bits  := RegEnable(ins.branchIndexOH, io.out.fire)

  phyRegManager.io.branchBackup  := branchBackup
  hiLoRegManager.io.branchBackup := branchBackup

  phyRegManager.io.branchRestore  <> io.branchRestore
  hiLoRegManager.io.branchRestore <> io.branchRestore

  phyRegManager.io.exceptionRestore  := io.exceptionRestore
  hiLoRegManager.io.exceptionRestore := io.exceptionRestore

  // IO
  stage.ready := io.out.ready && renameDone

  io.out.bits  := out
  io.out.valid := stage.valid && renameDone

  // debug
  if (p.DebugPipeViewLog) {
    when(RegNext(io.in.fire && ~io.flush, false.B)) {
      dprintln(p"PipeView:rename:0x${Hexadecimal(stage.bits.pc)}")
    }
    when(stage.valid && io.flush) {
      dprintln(p"PipeView:flush:0x${Hexadecimal(stage.bits.pc)}")
    }
  }
}
