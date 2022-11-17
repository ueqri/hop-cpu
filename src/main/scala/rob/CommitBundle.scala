package core.rob

import chisel3._

import core.CoreParameter

class CommitBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex = UInt(p.ROBEntryIndexBits.W)

  val ctrl   = new ROBCtrlBundle
  val result = new ROBResultBundle
}
