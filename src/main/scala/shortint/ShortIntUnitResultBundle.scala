package core.shortint

import chisel3._

import core.CoreParameter
import core.PhyRegWriteBundle

class ShortIntUnitResultBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val regWB = new PhyRegWriteBundle()

  val overflow = Bool()
}
