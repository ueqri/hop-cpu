package core.cp0

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.MircoInstruction.UnitOpBits
import core.rob.CommitBundle
import core.util.Stage

import CP0._

class CP0FrontEnd(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in = Flipped(DecoupledIO(CP0OperationBundle()))

    val out            = ValidIO(CP0ResultBundle())
    val outIssueWindow = ValidIO(UInt(p.IssueWindowIndexBits.W))

    val commit = Flipped(ValidIO(new CommitBundle()))

    val regRW = Flipped(new CP0RegfilesRegRWBundle)
  })

  val stage = Stage(io.in, io.flush)

  val inMfc0 = io.in.bits.op === CP0Mfc0
  val inMtc0 = io.in.bits.op === CP0Mtc0
  val mfc0   = RegEnable(inMfc0, enable = io.in.fire)
  val mtc0   = RegEnable(inMtc0, enable = io.in.fire)

  val sIdle :: sWaitingCommit :: Nil = Enum(2)

  val state    = RegInit(sIdle)
  val stateNxt = WireInit(state)
  state := Mux(io.flush, sIdle, stateNxt)

  val takeMtc0  = io.in.fire && inMtc0
  val getCommit = io.commit.fire && io.commit.bits.robIndex === stage.bits.robIndex

  switch(state) {
    is(sIdle) {
      when(takeMtc0) {
        stateNxt := sWaitingCommit
      }
    }

    is(sWaitingCommit) {
      when(getCommit) {
        stateNxt := sIdle
      }
    }
  }

  val writeCP0 = RegNext(state === sWaitingCommit && getCommit, init = false.B)
  stage.ready := RegNext(stateNxt === sIdle, init = false.B)

  io.out.valid           := stage.valid && RegNext(io.in.fire)
  io.out.bits.issueIndex := stage.bits.issueIndex
  io.out.bits.robIndex   := stage.bits.robIndex

  io.out.bits.regWB.phyReg := stage.bits.phyRegDst
  io.out.bits.regWB.data   := io.regRW.rdata
  io.out.bits.regWB.wen    := mfc0 && stage.bits.phyRegDst.orR

  io.outIssueWindow.valid := stage.valid && (~mtc0 || getCommit)
  io.outIssueWindow.bits  := stage.bits.issueIndex

  io.regRW.raddr := stage.bits.cp0Reg
  io.regRW.wen   := stage.valid && writeCP0
  io.regRW.waddr := stage.bits.cp0Reg
  io.regRW.wdata := stage.bits.data
}
