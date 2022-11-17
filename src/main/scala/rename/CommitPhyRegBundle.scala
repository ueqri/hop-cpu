package core.rename

import chisel3._
import core.CoreParameter

class CommitPhyRegBundle(implicit p: CoreParameter) extends Bundle {
  val log = UInt(5.W)
  val phy = UInt(p.PhyRegBits.W)
}
