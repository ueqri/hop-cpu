package core

import chisel3._
import chisel3.util._

import core.rob.ROBCtrlBundle
import core.dispatch.IDispatch
import core.issue.IIssue

import core.util.BitAlloctor
import core.util.Stage
import core.util.PrintDebugger

class IDispatchIssue(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in  = Flipped(DecoupledIO(MircoInstruction()))
    val out = DecoupledIO(MircoInstruction())

    val robEnq             = DecoupledIO(new ROBCtrlBundle())
    val robEnqAutoComplete = Output(Bool())
    val robEnqPtr          = Input(UInt(p.ROBEntryIndexBits.W))

    val writeBack = Vec(5, Flipped(ValidIO(UInt(p.IssueWindowIndexBits.W))))
    val commit    = Flipped(ValidIO(UInt(p.ROBEntryIndexBits.W)))
  })

  val dispatch = Module(new IDispatch)
  val issue    = Module(new IIssue)

  // debug
  if (p.DebugPipeViewLog) {
    val dispatchDebugPC       = RegNext(dispatch.io.in.bits.pc)
    val dispatchDebugRobIndex = RegNext(dispatch.io.robEnqPtr)
    when(RegNext(dispatch.io.in.fire && ~dispatch.io.flush, false.B)) {
      dprintln(p"PipeView:dispatch:0x${Hexadecimal(dispatchDebugPC)}:0x${Hexadecimal(dispatchDebugRobIndex)}")
    }

    val issueDebugPC       = issue.io.out.bits.pc
    val issueDebugRobIndex = issue.io.out.bits.robIndex
    when(issue.io.out.fire && ~issue.io.flush) {
      dprintln(p"PipeView:issue:0x${Hexadecimal(issueDebugPC)}:0x${Hexadecimal(issueDebugRobIndex)}")
    }.elsewhen(issue.io.out.fire && issue.io.flush) {
      dprintln(p"PipeView:flush:0x${Hexadecimal(issueDebugPC)}:0x${Hexadecimal(issueDebugRobIndex)}")
    }
  }

  io.in                 <> dispatch.io.in
  dispatch.io.robEnq    <> io.robEnq
  io.robEnqAutoComplete := dispatch.io.robEnqAutoComplete
  dispatch.io.robEnqPtr <> io.robEnqPtr
  dispatch.io.out       <> issue.io.in
  issue.io.out          <> io.out

  issue.io.writeBack <> io.writeBack
  issue.io.commit    := io.commit

  dispatch.io.flush := io.flush
  issue.io.flush    := io.flush
}
