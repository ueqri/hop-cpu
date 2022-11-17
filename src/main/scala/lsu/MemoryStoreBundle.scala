package core.lsu

import chisel3._

class MemoryStoreBundle extends Bundle {
  val addr = UInt(32.W)
  val data = UInt(32.W)
  val strb = UInt(4.W)
}
