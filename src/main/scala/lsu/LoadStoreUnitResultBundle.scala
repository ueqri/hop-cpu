package core.lsu

import chisel3._

import core.MircoInstruction
import core.MircoInstruction._
import core.CoreParameter
import core.PhyRegWriteBundle

class LoadStoreUnitResultBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val regWB = new PhyRegWriteBundle()

  val addrExceptionLoad  = Bool()
  val addrExceptionStore = Bool()
  val badVirtualAddr     = UInt(32.W)
}
