package core.lsu

import chisel3._

class StoreBufQueryBundle extends Bundle {
  val data = UInt(32.W)
  val strb = UInt(4.W)
  val hit  = Bool()
}
