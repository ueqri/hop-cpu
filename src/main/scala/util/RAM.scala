package core.util

import chisel3._

class DuelPortRam[T <: Data](gen: T, addrWidth: Int) extends Module {
  val io = IO(new Bundle {
    val wen   = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(gen)

    val raddr = Input(UInt(addrWidth.W))
    val rdata = Output(gen)
  })

  val mem = SyncReadMem(1 << addrWidth, gen)
  when(io.wen) {
    mem.write(io.waddr, io.wdata)
  }

  io.rdata := mem.read(io.raddr)
}
