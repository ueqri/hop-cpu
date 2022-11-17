package core.cp0

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.PhyRegWriteBundle

class CP0ResultBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val regWB = new PhyRegWriteBundle()
}

object CP0ResultBundle {
  def apply()(implicit p: CoreParameter) = new CP0ResultBundle()
}
