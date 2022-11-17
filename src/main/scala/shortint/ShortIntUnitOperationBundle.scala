package core.shortint

import chisel3._

import core.MircoInstruction
import core.MircoInstruction._
import core.CoreParameter
import core.PhyRegWriteBundle

class ShortIntUnitOperationBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val op        = UInt(UnitOpBits.W)
  val phyRegDst = UInt(p.PhyRegBits.W)

  val a     = UInt(32.W)
  val b     = UInt(32.W)
  val shamt = UInt(5.W)
}

object ShortIntUnitOperationBundle {
  def apply(ins: MircoInstruction, a: UInt, b: UInt, shmat: UInt)(implicit p: CoreParameter) = {
    val w = Wire(new ShortIntUnitOperationBundle)

    w.robIndex   := ins.robIndex
    w.issueIndex := ins.issueIndex
    w.op         := ins.op
    w.phyRegDst  := ins.regDst
    w.a          := a
    w.b          := b
    w.shamt      := shmat

    w.asTypeOf(w)
  }
}
