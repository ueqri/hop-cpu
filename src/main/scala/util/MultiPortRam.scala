package core.util

import chisel3._
import chisel3.util.log2Ceil

class MultiPortRam[T <: Data](private val gen: T, depth: Int, nWrite: Int, nRead: Int) extends Module {
  val addrBits = log2Ceil(depth)

  val io = IO(new Bundle {
    val w = Vec(
      nWrite,
      new Bundle {
        val en   = Input(Bool())
        val addr = Input(UInt(addrBits.W))
        val data = Input(gen)
      }
    )

    val r = Vec(
      nRead,
      new Bundle {
        val en   = Input(Bool())
        val addr = Input(UInt(addrBits.W))
        val data = Output(gen)
      }
    )
  })

  // TODO: impl XOR based MultiPortRam

  val mem = SyncReadMem(depth, gen, SyncReadMem.WriteFirst)

  for (w <- io.w) {
    when(w.en) {
      mem.write(w.addr, w.data)
    }
  }

  for (r <- io.r) {
    r.data := mem.read(r.addr, r.en)
  }
}

object MultiPortRam {
  def apply[T <: Data](gen: T, depth: Int, nWrite: Int, nRead: Int) = Module(
    new MultiPortRam(gen, depth, nWrite, nRead)
  )
}
