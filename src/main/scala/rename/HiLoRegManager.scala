package core.rename

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.util.BitAlloctor

class HiLoRegManager(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val curPhy = Output(UInt(p.PhyHiLoRegBits.W))

    val phyAlloc = Flipped(DecoupledIO())
    val phy      = Output(UInt(p.PhyHiLoRegBits.W))
    val phyOld   = Output(UInt(p.PhyHiLoRegBits.W))

    val commitReg  = Flipped(ValidIO(UInt(p.PhyHiLoRegBits.W)))
    val freePhyReg = Flipped(ValidIO(UInt(p.PhyHiLoRegBits.W)))

    val branchBackup  = Flipped(ValidIO(UInt(p.BranchIndexNum.W)))
    val branchRestore = Flipped(ValidIO(UInt(p.BranchIndexNum.W)))

    val exceptionRestore = Input(Bool())
  })

  val curPhy       = RegInit(0.U(p.PhyHiLoRegBits.W))
  val phyCommitted = Reg(UInt(p.PhyHiLoRegBits.W))

  val phyBranchBackup   = Reg(Vec(p.BranchIndexNum, UInt(p.PhyHiLoRegBits.W)))
  val branchFreePhyMask = Reg(Vec(p.BranchIndexNum, UInt(p.PhyHiLoRegNum.W)))

  val freePhy      = Module(new BitAlloctor(p.PhyHiLoRegNum))
  val freePhyIndex = OHToUInt(freePhy.io.out.bits)
  freePhy.io.flush     := false.B
  freePhy.io.out.ready := io.phyAlloc.valid && ~io.branchRestore.fire // TODO: weird

  when(io.phyAlloc.fire) {
    curPhy := freePhyIndex
  }

  val branchRestoreIndexOH = io.branchRestore.bits
  when(io.branchRestore.fire) { // restore have prio
    curPhy := Mux1H(branchRestoreIndexOH, phyBranchBackup)
  }

  when(io.exceptionRestore) {
    curPhy := phyCommitted
  }

  // Branch backup
  val branchBackupIndexOH = io.branchBackup.bits

  // when a delayslot inst that also alloces phy reg
  val r = Mux(io.phyAlloc.fire, freePhyIndex, curPhy)
  for (branchIndex <- 0 until p.BranchIndexNum) {
    when(io.branchBackup.fire && branchBackupIndexOH(branchIndex)) {
      phyBranchBackup(branchIndex) := r
    }
  }

  for ((mask, i) <- branchFreePhyMask.zipWithIndex) {
    when(io.branchBackup.fire && branchBackupIndexOH(i)) {
      mask := 0.U
    }.elsewhen(io.phyAlloc.fire) {
      mask := mask | freePhy.io.out.bits
    }
  }

  // free phy
  val freePhyReg      = io.freePhyReg.fire
  val phyReg2Free     = io.freePhyReg.bits
  val phyReg2FreeMask = Mux(freePhyReg, UIntToOH(phyReg2Free), 0.U)
  val notCancelFirst  = RegInit(false.B)
  when(freePhyReg) {
    notCancelFirst := true.B
  }

  val restore           = io.branchRestore.fire
  val freePhyBackupMask = Mux(restore, Mux1H(branchRestoreIndexOH, branchFreePhyMask), 0.U)

  freePhy.io.in.valid := restore || (freePhyReg && notCancelFirst)
  freePhy.io.in.bits  := phyReg2FreeMask | freePhyBackupMask

  // commit
  when(io.commitReg.fire) {
    phyCommitted := io.commitReg.bits
  }

  // IO
  io.curPhy := curPhy
  io.phy    := freePhyIndex
  io.phyOld := curPhy

  io.phyAlloc.ready := freePhy.io.out.valid
}
