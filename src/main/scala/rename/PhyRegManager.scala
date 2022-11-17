package core.rename

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.util.BitAlloctor

class PhyRegManager(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val log2phyMap = Vec(2, Flipped(new Log2PhyMapBundle()))

    val phyAlloc = Flipped(DecoupledIO(UInt(5.W)))
    val phy      = Output(UInt(p.PhyRegBits.W))
    val phyOld   = Output(UInt(p.PhyRegBits.W))

    val commitReg  = Flipped(ValidIO(new CommitPhyRegBundle))
    val freePhyReg = Flipped(ValidIO(UInt(p.PhyRegBits.W)))

    val branchBackup  = Flipped(ValidIO(UInt(p.BranchIndexNum.W)))
    val branchRestore = Flipped(ValidIO(UInt(p.BranchIndexNum.W)))

    val exceptionRestore = Input(Bool())
  })

  val log2phy          = RegInit(VecInit.fill(32) { 0.U(p.PhyRegBits.W) })
  val log2phyCommitted = RegInit(VecInit.fill(32) { 0.U(p.PhyRegBits.W) })

  val log2phyBranchBackup = Reg(Vec(p.BranchIndexNum, Vec(32, UInt(p.PhyRegBits.W))))
  val branchFreePhyMask   = Reg(Vec(p.BranchIndexNum, UInt((p.PhyRegNum - 1).W)))

  val freePhy      = Module(new BitAlloctor(p.PhyRegNum - 1)) // reg 0 is always used
  val freePhyIndex = OHToUInt(Cat(freePhy.io.out.bits, 0.U(1.W)))
  freePhy.io.flush     := false.B
  freePhy.io.out.ready := io.phyAlloc.valid && ~io.branchRestore.fire // TODO: weird

  val phyAllocLog = io.phyAlloc.bits
  // log2phy logic
  when(io.phyAlloc.fire) {
    log2phy(phyAllocLog) := freePhyIndex
  }

  val branchRestoreIndexOH = io.branchRestore.bits
  val log2phyBackup        = Mux1H(branchRestoreIndexOH, log2phyBranchBackup)
  when(io.branchRestore.fire) { // restore have prio
    for (i <- 0 until 32) {
      log2phy(i) := log2phyBackup(i)
    }
  }

  when(io.exceptionRestore) {
    for (i <- 0 until 32) {
      log2phy(i) := log2phyCommitted(i)
    }
  }

  // Branch backup
  val branchBackupIndexOH = io.branchBackup.bits
  val regDstLogicOH       = UIntToOH(phyAllocLog)
  for (i <- 0 until 32) {
    // when a delayslot inst that also alloces phy reg
    val r = Mux(regDstLogicOH(i) && io.phyAlloc.fire, freePhyIndex, log2phy(i))

    for (branchIndex <- 0 until p.BranchIndexNum) {
      when(io.branchBackup.fire && branchBackupIndexOH(branchIndex)) {
        log2phyBranchBackup(branchIndex)(i) := r
      }
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
  val phyReg2FreeMask = Mux(freePhyReg, UIntToOH(phyReg2Free) >> 1, 0.U)

  val restore           = io.branchRestore.fire
  val freePhyBackupMask = Mux(restore, Mux1H(branchRestoreIndexOH, branchFreePhyMask), 0.U)

  freePhy.io.in.valid := restore || freePhyReg
  freePhy.io.in.bits  := phyReg2FreeMask | freePhyBackupMask

  // commit
  when(io.commitReg.fire) {
    log2phyCommitted(io.commitReg.bits.log) := io.commitReg.bits.phy
  }

  // IO
  io.log2phyMap(0).phy := log2phy(io.log2phyMap(0).log)
  io.log2phyMap(1).phy := log2phy(io.log2phyMap(1).log)

  io.phyAlloc.ready := freePhy.io.out.valid

  io.phy    := freePhyIndex
  io.phyOld := log2phy(phyAllocLog)
}
