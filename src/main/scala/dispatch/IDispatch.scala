package core.dispatch

import chisel3._
import chisel3.util._

import core.rob.ROBCtrlBundle

import core.CoreParameter
import core.MircoInstruction
import core.util.BitAlloctor
import core.util.Stage
import core.util.PrintDebugger

class IDispatch(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in  = Flipped(DecoupledIO(MircoInstruction()))
    val out = DecoupledIO(MircoInstruction())

    val robEnq             = DecoupledIO(new ROBCtrlBundle())
    val robEnqAutoComplete = Output(Bool())
    val robEnqPtr          = Input(UInt(p.ROBEntryIndexBits.W))

  })
  val dispatchQueue =
    Module(
      new Queue(
        MircoInstruction(),
        p.dispatchQueueSize,
        pipe           = true,
        flow           = true,
        useSyncReadMem = true,
        hasFlush       = true
      )
    )

  // when autoComplete is asserted, instruction is sent to rob only
  val autoComplete = io.in.bits.autoComplete

  dispatchQueue.io.flush.get         := io.flush
  dispatchQueue.io.enq.valid         := io.in.valid && ~autoComplete && io.robEnq.ready
  dispatchQueue.io.enq.bits          := io.in.bits
  dispatchQueue.io.enq.bits.robIndex := io.robEnqPtr

  io.robEnq.valid := io.in.valid && dispatchQueue.io.enq.ready

  io.robEnq.bits        := ROBCtrlBundle(io.in.bits)
  io.robEnqAutoComplete := io.in.bits.autoComplete

  io.in.ready := io.robEnq.ready && (dispatchQueue.io.enq.ready || ~autoComplete)

  io.out <> dispatchQueue.io.deq
}
