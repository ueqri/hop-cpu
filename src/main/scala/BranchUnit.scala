package core

import chisel3._
import chisel3.util.DecoupledIO
import chisel3.util.ValidIO
import chisel3.util.Cat

import core.BranchUnit._
import core.util.BitAlloctor
import core.MircoInstruction.UnitOpBits
import core.util.Stage
import chisel3.util.UIntToOH
import chisel3.util.Mux1H

class BranchUnitOperationBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val op = UInt(UnitOpBits.W)

  val predictTake = Bool()
  val phyRegDst   = UInt(p.PhyRegBits.W)
  val link        = Bool()

  val a       = UInt(32.W)
  val b       = UInt(32.W)
  val imm     = UInt(17.W)
  val pcPlus4 = UInt(32.W)
}

object BranchUnitOperationBundle {
  def apply(ins: MircoInstruction, a: UInt, b: UInt)(implicit p: CoreParameter) = {
    val w = Wire(new BranchUnitOperationBundle)

    w.robIndex    := ins.robIndex
    w.issueIndex  := ins.issueIndex
    w.op          := ins.op
    w.phyRegDst   := ins.regDst
    w.a           := a
    w.link        := ins.isLink
    w.b           := b
    w.imm         := ins.imm
    w.pcPlus4     := ins.pc +& 4.U
    w.predictTake := ins.predictTaken

    w.asTypeOf(w)
  }
}

class BranchUnitResultBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val regWB = new PhyRegWriteBundle()

  val pcRedirectAddr = UInt(32.W)
  val pcRedirect     = Bool()
}

class BranchUnit(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val in    = Flipped(DecoupledIO(new BranchUnitOperationBundle()))

    val out = ValidIO(new BranchUnitResultBundle())
  })
  val stage      = Stage(io.in, io.flush)
  val imm        = Cat(Cat(Seq.fill(14) { stage.bits.imm(16) }), stage.bits.imm, 0.U(2.W))
  val branchAddr = stage.bits.pcPlus4 +& imm
  val linkAddr   = stage.bits.pcPlus4 +& 4.U

  val opsel = UIntToOH(stage.bits.op)
  val beq   = opsel(BranchUnitOPBeq)
  val bne   = opsel(BranchUnitOPBneq)
  val blez  = opsel(BranchUnitOPBlez)
  val bltz  = opsel(BranchUnitOPBltz)
  val bgez  = opsel(BranchUnitOPBgez)
  val bgtz  = opsel(BranchUnitOPBgtz)
  val jr    = opsel(BranchUnitOPJr)
  val jmp   = opsel(BranchUnitOPJmp)

  val a    = stage.bits.a
  val b    = stage.bits.b
  val aEqB = a === b
  val taken = Mux1H(
    Seq(
      beq  -> aEqB,
      bne  -> ~aEqB,
      blez -> (a.asSInt <= 0.S),
      bltz -> (a.asSInt < 0.S),
      bgez -> (a.asSInt >= 0.S),
      bgtz -> (a.asSInt > 0.S),
      jr   -> ~stage.bits.predictTake,
      jmp  -> stage.bits.predictTake
    )
  )

  val targetPC = Mux(jr, a, branchAddr)

  //IO
  stage.ready := true.B

  io.out.valid               := stage.valid
  io.out.bits.issueIndex     := stage.bits.issueIndex
  io.out.bits.robIndex       := stage.bits.robIndex
  io.out.bits.regWB.phyReg   := stage.bits.phyRegDst
  io.out.bits.regWB.wen      := stage.bits.link
  io.out.bits.regWB.data     := linkAddr
  io.out.bits.pcRedirect     := taken =/= stage.bits.predictTake
  io.out.bits.pcRedirectAddr := Mux(stage.bits.predictTake, linkAddr, targetPC)
}

object BranchUnit {
  val BranchUnitOPBeq  = 0.U(UnitOpBits.W)
  val BranchUnitOPBneq = 1.U(UnitOpBits.W)
  val BranchUnitOPBlez = 2.U(UnitOpBits.W)
  val BranchUnitOPBltz = 3.U(UnitOpBits.W)
  val BranchUnitOPBgez = 4.U(UnitOpBits.W)
  val BranchUnitOPBgtz = 5.U(UnitOpBits.W)
  val BranchUnitOPJr   = 6.U(UnitOpBits.W)
  val BranchUnitOPJmp  = 7.U(UnitOpBits.W)
}
