package core.lsu

import chisel3._

import core.CoreParameter
import core.MircoInstruction._

class StoreBufferEntry(implicit p: CoreParameter) extends Bundle {
  val addrUpper30 = UInt(30.W) // lower 2 bits are always zeros
  val strb        = UInt(4.W)
  val data        = Vec(4, UInt(8.W))

  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val branchMask = UInt(p.BranchIndexNum.W)
}
