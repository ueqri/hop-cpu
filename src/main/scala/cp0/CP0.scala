package core.cp0

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.MircoInstruction.UnitOpBits
import core.rob.CommitBundle
import core.util.Stage

import CP0._

class CP0(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in             = Flipped(DecoupledIO(CP0OperationBundle()))
    val out            = ValidIO(CP0ResultBundle())
    val outIssueWindow = ValidIO(UInt(p.IssueWindowIndexBits.W))

    val commit = Flipped(ValidIO(new CommitBundle()))

    val exception       = Flipped(ValidIO(new ExceptionBundle))
    val EPC             = Output(UInt(32.W))
    val exceptionReturn = Input(Bool())

    val writeBadVAddr = Flipped(ValidIO(UInt(32.W)))

    val int = ValidIO(UInt(3.W))
  })

  val frontEnd   = Module(new CP0FrontEnd)
  val regfile    = Module(new CP0Regfiles)
  val intArbiter = Module(new IntArbiter)

  io.in             <> frontEnd.io.in
  io.out            <> frontEnd.io.out
  io.outIssueWindow <> frontEnd.io.outIssueWindow
  io.commit         <> frontEnd.io.commit

  regfile.io.regRW <> frontEnd.io.regRW

  regfile.io.exception       <> io.exception
  regfile.io.exceptionReturn <> io.exceptionReturn
  io.EPC                     := regfile.io.EPC

  regfile.io.writeBadVAddr := io.writeBadVAddr

  frontEnd.io.flush := io.flush

  intArbiter.io.intPending := regfile.io.intPending
  intArbiter.io.intMask    := regfile.io.intMask
  intArbiter.io.intEnable  := regfile.io.intEnable
  intArbiter.io.EXL        := regfile.io.EXL

  io.int := intArbiter.io.int
}

object CP0 {
  val CP0Mfc0 = 0.U(UnitOpBits.W)
  val CP0Mtc0 = 1.U(UnitOpBits.W)
}
