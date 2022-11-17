package core.cp0

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.MircoInstruction
import core.MircoInstruction.UnitOpBits

class CP0OperationBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val op        = UInt(UnitOpBits.W)
  val phyRegDst = UInt(p.PhyRegBits.W)

  val cp0Reg = UInt(5.W)
  val sel    = UInt(3.W)
  val data   = UInt(32.W)
}

object CP0OperationBundle {
  def apply()(implicit p: CoreParameter) = new CP0OperationBundle

  def apply(ins: MircoInstruction, data: UInt)(implicit p: CoreParameter) = {
    val w = Wire(new CP0OperationBundle)

    w.robIndex   := ins.robIndex
    w.issueIndex := ins.issueIndex
    w.op         := ins.op
    w.phyRegDst  := ins.regDst
    w.data       := data
    w.cp0Reg     := ins.imm(15, 11)
    w.sel        := ins.imm(2, 0)

    w.asTypeOf(w)
  }
}
