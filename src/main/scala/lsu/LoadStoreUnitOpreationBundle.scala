package core.lsu

import chisel3._

import core.MircoInstruction
import core.MircoInstruction._
import core.CoreParameter

class LoadStoreUnitOpreationBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)
  val branchMask = UInt(p.BranchIndexNum.W)

  val phyRegDst = UInt(p.PhyRegBits.W)
  val op        = UInt(UnitOpBits.W)

  val data   = UInt(32.W)
  val base   = UInt(32.W)
  val offset = UInt(17.W)
}

object LoadStoreUnitOpreationBundle {
  def apply(ins: MircoInstruction, data: UInt, base: UInt, offset: UInt)(implicit p: CoreParameter) = {
    val w = Wire(new LoadStoreUnitOpreationBundle)

    w.robIndex   := ins.robIndex
    w.issueIndex := ins.issueIndex
    w.branchMask := ins.branchMask
    w.op         := ins.op
    w.phyRegDst  := ins.regDst
    w.data       := data
    w.base       := base
    w.offset     := offset

    w.asTypeOf(w)
  }
}
