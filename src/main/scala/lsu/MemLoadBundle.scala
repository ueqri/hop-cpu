package core.lsu

import chisel3._

import core.MircoInstruction
import core.MircoInstruction._
import core.CoreParameter

class MemLoadBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val phyRegDst = UInt(p.PhyRegBits.W)
  val op        = UInt(UnitOpBits.W)

  val addr = UInt(32.W)
  val data = UInt(32.W)
}
