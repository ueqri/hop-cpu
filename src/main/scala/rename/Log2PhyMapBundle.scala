package core.rename

import chisel3._
import core.CoreParameter

class Log2PhyMapBundle(implicit p: CoreParameter) extends Bundle {
  val log = Output(UInt(5.W))
  val phy = Input(UInt(p.PhyRegBits.W))
}
