package core.util

import chisel3._
import chisel3.util.log2Ceil
import chisel3.util.isPow2
import chisel3.util.RegEnable

/** MultiPortQueueDecoupledIO
  * Similar to DecoupledIO, except that valid and ready singal are unsigned numbers.
  * Note that the data is not ordered, but it can be reorder by rotate shifting with an offset given by the queue.
  * Note that users must ensure that enq.valid <= enq.ready and deq.ready <= deq.valid.
  *
  * @param gen
  * @param ports
  */
class MultiPortQueueDecoupledIO[T <: Data](private val gen: T, val ports: Int) extends Bundle {
  private val portBits = log2Ceil(ports + 1)

  val valid = Output(UInt(portBits.W))
  val ready = Input(UInt(portBits.W))
  val data  = Output(Vec(ports, gen))
}

object MultiPortQueueDecoupledIO {
  def apply[T <: Data](gen: T, ports: Int) = new MultiPortQueueDecoupledIO(gen, ports)
}

class MultiPortQueueIO[T <: Data](private val gen: T, ports: Int) extends Bundle {
  val flush = Output(Bool())

  val enq = new MultiPortQueueDecoupledIO(gen, ports)
  val deq = Flipped(new MultiPortQueueDecoupledIO(gen, ports))

  val enq_offset = Input(UInt(log2Ceil(ports + 1).W))
  val deq_offset = Input(UInt(log2Ceil(ports + 1).W))
}

class MultiPortQueuePtr(rows: Int, cols: Int) extends Module {
  val rowBits  = log2Ceil(rows) + 1
  val colBits  = log2Ceil(cols)
  val colBits1 = log2Ceil(cols + 1)
  val ptrBits  = log2Ceil(rows * cols) + 1

  val io = IO(new Bundle {
    val flush = Input(Bool())

    val enq_valid = Input(UInt(colBits1.W))
    val enq_ready = Output(UInt(colBits1.W))
    val enq_row   = Output(UInt(rowBits.W))
    val enq_col   = Output(UInt(colBits.W))
    val enq_mask  = Output(UInt((cols * 2).W))
    val nFree     = Output(UInt((ptrBits + 1).W))

    val deq_ready = Input(UInt(colBits1.W))
    val deq_valid = Output(UInt(colBits1.W))
    val deq_row   = Output(UInt(rowBits.W))
    val deq_col   = Output(UInt(colBits.W))
    val deq_mask  = Output(UInt((cols * 2).W))
    val nValid    = Output(UInt((ptrBits + 1).W))
  })

  val enqPtr = RegInit(0.U(ptrBits.W))
  val deqPtr = RegInit(0.U(ptrBits.W))

  val nValid = RegInit(0.U((ptrBits + 1).W))
  val nFree  = RegInit((rows * cols).U((ptrBits + 1).W))

  val diff = io.enq_valid.zext - io.deq_ready.zext
  nValid := (nValid.zext + diff).asUInt
  nFree  := (nFree.zext - diff).asUInt

  enqPtr := enqPtr +& io.enq_valid
  deqPtr := deqPtr +& io.deq_ready

  io.enq_col   := enqPtr(colBits - 1, 0)
  io.enq_row   := enqPtr(colBits + rowBits - 1, colBits)
  io.enq_mask  := ((~(-1.S((cols * 2 - 1).W) << io.enq_valid)).pad(cols * 2) << io.enq_col).asUInt
  io.enq_ready := Mux(nFree >= cols.U, cols.U, nFree)
  io.nFree     := nFree

  io.deq_col   := deqPtr(colBits - 1, 0)
  io.deq_row   := deqPtr(colBits + rowBits - 1, colBits)
  io.deq_mask  := ((~(-1.S((cols * 2 - 1).W) << io.deq_ready)).pad(cols * 2) << io.deq_col).asUInt
  io.deq_valid := Mux(nValid >= cols.U, cols.U, nValid)
  io.nValid    := nValid
}

class MultiPortQueue[T <: Data](val gen: T, depth: Int, ports: Int) extends Module {
  require(isPow2(depth) && isPow2(ports))
  // TODO: support flow and pipe mode
  val rows = depth / ports
  val cols = ports

  val io = IO(Flipped(new MultiPortQueueIO(gen, ports)))

  val ptrs = Module(new MultiPortQueuePtr(rows, cols)).io
  ptrs.flush     := io.flush
  ptrs.enq_valid := io.enq.valid
  ptrs.deq_ready := io.deq.ready

  val ram = Module(new DuelPortRam(Vec(cols * 2, gen), rows / 2))

  val enqBuf = Reg(Vec(4, Vec(cols, gen)))
  val deqBuf = Reg(Vec(4, Vec(cols, gen)))

  assert(io.enq.valid <= io.enq.ready)
  assert(io.deq.valid >= io.deq.ready)

  val row2Gap     = (ptrs.enq_row >> 1) - (ptrs.deq_row >> 1)
  val row2GapLt2  = row2Gap <= 2.U;
  val row2GapOne  = row2Gap <= 1.U;
  val row2GapZero = row2Gap === 0.U;

  // enq to buf
  for (i <- 0 until 4) {
    for (col <- 0 until cols) {
      val i3 = (i + 3) % 4; // === (i - 1) % 4

      when(
        (ptrs.enq_mask(col) && ptrs.enq_row(1, 0) === i.U) ||
          (ptrs.enq_mask(col + cols) && ptrs.enq_row(1, 0) === i3.U)
      ) {
        enqBuf(i)(col) := io.enq.data(col)
      }
    }
  }

  // buf 2 deq
  val deqBufActiveRow0 = deqBuf(ptrs.deq_row(1, 0))
  val deqBufActiveRow1 = VecInit.tabulate(4) { i => deqBuf((i + 1) % 4) }(ptrs.deq_row(1, 0))

  for (col <- 0 until cols) {
    io.deq.data(col) := Mux(ptrs.deq_mask(col), deqBufActiveRow0(col), deqBufActiveRow1(col))
  }

  // buf <-> ram
  val enqBufPush = ptrs.enq_mask(ports - 1) && ptrs.enq_row(0) // enq_mask(ports -1 ) means that one row is to be filled
  // ptrs.enq_row(0) means that it is an odd row
  val deqBufLoad   = ptrs.deq_mask(ports - 1) && ptrs.deq_row(0)
  val deqBufLoad_d = RegNext(deqBufLoad)

  val wen   = RegInit(false.B)
  val waddr = RegEnable(ptrs.enq_row >> 1, enqBufPush)
  val wdata = Mux(waddr(0), VecInit(enqBuf(2) ++ enqBuf(3)), VecInit(enqBuf(0) ++ enqBuf(1)))
  val ren   = deqBufLoad
  val raddr = (ptrs.deq_row >> 1) + 2.U
  when(enqBufPush) { wen := true.B }
    .elsewhen(!ren) { wen := false.B }

  ram.io.wen   := wen && !ren
  ram.io.waddr := waddr
  ram.io.wdata := wdata
  ram.io.raddr := raddr

  for (col <- 0 until cols) {
    when(ptrs.deq_row(1) && deqBufLoad_d) {
      deqBuf(0)(col) := Mux(row2GapLt2, enqBuf(0)(col), ram.io.rdata(col))
      deqBuf(1)(col) := Mux(row2GapLt2, enqBuf(1)(col), ram.io.rdata(col + cols))
    }

    when(!ptrs.deq_row(1) && deqBufLoad_d) {
      deqBuf(2)(col) := Mux(row2GapLt2, enqBuf(2)(col), ram.io.rdata(col))
      deqBuf(3)(col) := Mux(row2GapLt2, enqBuf(3)(col), ram.io.rdata(col + cols))
    }

    for (i <- 0 until 4) {
      val i3       = (i + 3) % 4; // === (i - 1) % 4
      val corssGap = if (i % 2 == 0) row2GapZero else row2GapOne
      when(
        (row2GapOne && ptrs.enq_mask(col) && ptrs.enq_row(1, 0) === i.U) ||
          (corssGap && ptrs.enq_mask(col + cols) && ptrs.enq_row(1, 0) === i3.U)
      ) {

        deqBuf(i)(col) := io.enq.data(col)
      }
    }
  }

  io.enq_offset := ptrs.enq_col
  io.enq.ready  := ptrs.enq_ready

  io.deq_offset := ptrs.deq_col
  io.deq.valid  := ptrs.deq_valid
}

object MultiPortQueue {
  def apply[T <: Data](gen: T, depth: Int, ports: Int) = Module(new MultiPortQueue(gen, depth, ports))
}
