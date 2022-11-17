package core.issue

import chisel3._
import chisel3.util._

import core.rob.ROBCtrlBundle

import core.CoreParameter
import core.MircoInstruction
import core.util.BitAlloctor
import core.util.Stage
import core.util.PrintDebugger

class IIssue(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in  = Flipped(DecoupledIO(MircoInstruction()))
    val out = DecoupledIO(MircoInstruction())

    val writeBack = Vec(5, Flipped(ValidIO(UInt(p.IssueWindowIndexBits.W))))
    val commit    = Flipped(ValidIO(UInt(p.ROBEntryIndexBits.W)))
  })

  val indexAlloc      = Module(new BitAlloctor(p.IssueWindowSize))
  val robToIssueIndex = Mem(p.ROBDepth, UInt(p.IssueWindowIndexBits.W))

  val insValid     = ~indexAlloc.io.freeBits
  val insRegDst    = Reg(Vec(p.IssueWindowSize, UInt(p.PhyRegBits.W)))
  val insRegWrite  = Reg(Vec(p.IssueWindowSize, Bool()))
  val insIsStore   = Reg(Vec(p.IssueWindowSize, Bool()))
  val insIsLoad    = Reg(Vec(p.IssueWindowSize, Bool()))
  val insWriteHiLo = Reg(Vec(p.IssueWindowSize, Bool()))
  val insIsBarrier = Reg(Vec(p.IssueWindowSize, Bool()))
  val insRegHiLo   = Reg(Vec(p.IssueWindowSize, UInt(p.PhyHiLoRegBits.W)))
  val insRam       = Mem(p.IssueWindowSize, MircoInstruction())

  val alloc      = io.in.valid
  val allocReady = indexAlloc.io.out.valid
  val allocDone  = allocReady && alloc

  // instruction to dispatch
  val inIns     = io.in.bits
  val inIndexOH = indexAlloc.io.out.bits
  val inIndex   = OHToUInt(inIndexOH)
  val inFire    = io.in.fire

  when(inFire) {
    insRam(inIndex) := inIns
  }
  for (i <- 0 until p.IssueWindowSize) {
    when(inFire && inIndexOH(i)) {
      insRegDst(i)    := inIns.regDst
      insRegWrite(i)  := inIns.regDstWrite
      insIsStore(i)   := inIns.isStore
      insWriteHiLo(i) := inIns.writeHiLo
      insRegHiLo(i)   := inIns.hiLoReg
      insIsLoad(i)    := inIns.isLoad
      insIsBarrier(i) := inIns.isBarrier
    }
  }

  // dependency gen
  val inInsDepRow = Wire(Vec(p.IssueWindowSize, Bool()))
  for ((dep, i) <- inInsDepRow.zipWithIndex) {
    val reg0Dep  = inIns.reg0 === insRegDst(i) && insRegWrite(i) && inIns.reg0Req
    val reg1Dep  = inIns.reg1 === insRegDst(i) && insRegWrite(i) && inIns.reg1Req
    val hiloDep  = inIns.hiLoReg === insRegHiLo(i) && insWriteHiLo(i) && inIns.reqHiLo
    val loadDep  = inIns.isLoad && insIsStore(i)
    val storeDep = inIns.isStore && (insIsStore(i) || insIsLoad(i))

    val barrierSelf = inIns.isBarrier
    val barriered   = insIsBarrier(i)
    val bar         = barrierSelf || barriered

    dep := insValid(i) && (reg0Dep || reg1Dep || hiloDep || loadDep || storeDep || bar)
  }

  val commitFreeIndexOH    = Mux(io.commit.fire, UIntToOH(robToIssueIndex(io.commit.bits)), 0.U)
  val writeBackFreeIndexOH = io.writeBack.map(wb => Mux(wb.fire, UIntToOH(wb.bits), 0.U)).reduce(_ | _)

  val freeIndexOH = commitFreeIndexOH | writeBackFreeIndexOH
  indexAlloc.io.in.valid := (Seq(io.commit.fire) ++ io.writeBack.map(_.fire)).reduce(_ || _)
  indexAlloc.io.in.bits  := freeIndexOH

  val dependencyMetrix = Reg(Vec(p.IssueWindowSize, Vec(p.IssueWindowSize, Bool())))
  for ((row, i) <- dependencyMetrix.zipWithIndex) {
    for ((dep, j) <- row.zipWithIndex) {
      when(freeIndexOH(j)) {
        dep := false.B
      }.elsewhen(inFire && inIndexOH(i)) {
        dep := inInsDepRow(j)
      }
    }
  }

  // insturction to issue
  val insNotIssued = RegInit(VecInit.fill(p.IssueWindowSize) { false.B })

  val issueIndexOH = Wire(UInt(p.IssueWindowSize.W))
  val issueIndex   = OHToUInt(issueIndexOH)
  val issueOk      = Wire(Bool())
  val issueIns     = insRam(issueIndex)
  val outFire      = io.out.fire

  val issuePendingOH = Seq.fill(p.IssueWindowSize) { Wire(Bool()) }
  for ((is, i) <- issuePendingOH.zipWithIndex) {
    is := ~Cat(dependencyMetrix(i)).orR && insNotIssued(i)
  }
  issueIndexOH := PriorityEncoderOH(Cat(issuePendingOH.reverse)) //
  issueOk      := issueIndexOH.orR

  for ((notIssued, i) <- insNotIssued.zipWithIndex) {
    when(inFire && inIndexOH(i)) {
      notIssued := true.B
    }

    when(outFire && issueIndexOH(i)) {
      notIssued := false.B
    }

    when(io.flush) {
      notIssued := false.B
    }
  }

  when(outFire) {
    robToIssueIndex(issueIns.robIndex) := issueIndex
  }

  // IO
  io.in.ready := allocReady

  io.out.valid           := issueOk
  io.out.bits            := issueIns
  io.out.bits.issueIndex := issueIndex

  indexAlloc.io.flush     := io.flush
  indexAlloc.io.out.ready := alloc
}
