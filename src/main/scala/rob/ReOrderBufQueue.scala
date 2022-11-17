package core.rob

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.util.MultiPortRam

class ReOrderBufQueue(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val setAllValid = Input(Bool())

    val enq             = Flipped(DecoupledIO(new ROBCtrlBundle()))
    val enqAutoComplete = Input(Bool())
    val enqPtr          = Output(UInt(p.ROBEntryIndexBits.W))

    val deq    = DecoupledIO(new CommitBundle())
    val deqPtr = Output(UInt(p.ROBEntryIndexBits.W))

    val writeBack = Flipped(Vec(5, ValidIO(new ROBWriteBackBundle))) // TODO: remove fix para 4
  })

  require(isPow2(p.ROBDepth))

  val resultRam   = MultiPortRam(new ROBResultBundle, p.ROBDepth, 5, 1) // TODO: remove fix para 4
  val resultValid = Reg(Vec(p.ROBDepth, Bool()))
  val ctrlRam     = SyncReadMem(p.ROBDepth, new ROBCtrlBundle, SyncReadMem.WriteFirst)

  val enqPtr    = Counter(p.ROBDepth)
  val deqPtr    = Counter(p.ROBDepth)
  val maybeFull = RegInit(false.B)

  val deqReadAddr = Mux(io.deq.fire, deqPtr.value +& 1.U, deqPtr.value)

  // data path
  // enqueue
  when(io.enq.fire) {
    ctrlRam(enqPtr.value)     := io.enq.bits
    resultValid(enqPtr.value) := io.enqAutoComplete
  }

  // results
  for ((w, res) <- resultRam.io.w.zip(io.writeBack)) {
    w.addr := res.bits.robIndex
    w.data := res.bits.result
    w.en   := res.fire

    when(res.fire) {
      resultValid(res.bits.robIndex) := true.B
    }
  }

  when(io.setAllValid) {
    for (valid <- resultValid)
      valid := true.B
  }

  // dequeue
  val deqCtrl   = ctrlRam.read(deqReadAddr)
  val deqResult = resultRam.io.r(0).data
  resultRam.io.r(0).addr := deqReadAddr
  resultRam.io.r(0).en   := true.B

  // ctrl gen
  when(io.enq.fire) {
    enqPtr.inc()
  }

  when(io.deq.fire) {
    deqPtr.inc()
  }

  when(io.enq.fire =/= io.deq.fire) {
    maybeFull := io.enq.fire
  }

  // flush
  when(io.flush) {
    for (valid <- resultValid)
      valid := false.B
    enqPtr.reset()
    deqPtr.reset()
    maybeFull := false.B
  }

  val empty = enqPtr.value === deqPtr.value && !maybeFull
  val full  = enqPtr.value === deqPtr.value && maybeFull

  // IO
  io.enq.ready := !full
  io.enqPtr    := enqPtr.value

  io.deq.valid         := !empty && resultValid(deqPtr.value)
  io.deq.bits.robIndex := deqPtr.value
  io.deq.bits.ctrl     := deqCtrl
  io.deq.bits.result   := deqResult
  io.deqPtr            := deqPtr.value
}
