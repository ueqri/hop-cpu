package core

import chisel3._
import chisel3.util._

import core.util.Stage
import MircoInstruction._
import LongIntExecUnit._

class LongIntExecUnitOperationBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val op            = UInt(UnitOpBits.W)
  val phyRegDst     = UInt(p.PhyRegBits.W)
  val hiloPhyRegDst = UInt(p.PhyHiLoRegBits.W)

  val a    = UInt(32.W)
  val b    = UInt(32.W)
  val hilo = new HiLoRegPack()
}

object LongIntExecUnitOperationBundle {
  def apply(ins: MircoInstruction, a: UInt, b: UInt, hilo: HiLoRegPack)(implicit p: CoreParameter) = {
    val w = Wire(new LongIntExecUnitOperationBundle)

    w.robIndex      := ins.robIndex
    w.issueIndex    := ins.issueIndex
    w.op            := ins.op
    w.phyRegDst     := ins.regDst
    w.hiloPhyRegDst := ins.hiLoReg
    w.a             := a
    w.b             := b
    w.hilo          := hilo

    w.asTypeOf(w)
  }
}

class LongIntExecUnitResultBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex   = UInt(p.ROBEntryIndexBits.W)
  val issueIndex = UInt(p.IssueWindowIndexBits.W)

  val regWB = new PhyRegWriteBundle()

  val hiloWB = new PhyHiLoRegWriteBundle()
}

class LongIntExecUnit(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in  = Flipped(DecoupledIO(new LongIntExecUnitOperationBundle))
    val out = ValidIO(new LongIntExecUnitResultBundle)
  })

  val stage = Stage(io.in, io.flush)
  val op    = stage.bits.op

  val opOH   = UIntToOH(op)
  val opMul  = opOH(LongIntMult)
  val opMulu = opOH(LongIntMultu)
  val opDiv  = opOH(LongIntDiv)
  val opDivu = opOH(LongIntDivu)
  val opMfl  = opOH(LongIntMFL)
  val opMfh  = opOH(LongIntMFH)
  val opMtl  = opOH(LongIntMTL)
  val opMth  = opOH(LongIntMTH)

  val hiloRegWrite = opMul | opMulu | opDiv | opDivu | opMtl | opMth
  val regDstWrite  = opMfl | opMfh

  val mul   = stage.bits.a.asSInt * stage.bits.b.asSInt
  val mulHi = mul(63, 32) //TODO:
  val mulLo = mul(31, 0)

  val mulu   = stage.bits.a * stage.bits.b
  val muluHi = mulu(63, 32) //TODO:
  val muluLo = mulu(31, 0)

  val divHi = (stage.bits.a.asSInt % stage.bits.b.asSInt).asUInt(31, 0)
  val divLo = (stage.bits.a.asSInt / stage.bits.b.asSInt).asUInt(31, 0)

  val divuHi = stage.bits.a % stage.bits.b
  val divuLo = stage.bits.a / stage.bits.b

  val resLo = Mux1H(
    Seq(
      opMul  -> mulLo,
      opMulu -> muluLo,
      opDiv  -> divLo,
      opDivu -> divuLo,
      opMfl  -> stage.bits.hilo.lo,
      opMfh  -> stage.bits.hilo.hi,
      opMtl  -> stage.bits.a,
      opMth  -> stage.bits.hilo.lo
    )
  )

  val resHi = Mux1H(
    Seq(
      opMul  -> mulHi,
      opMulu -> muluHi,
      opDiv  -> divHi,
      opDivu -> divuHi,
      opMtl  -> stage.bits.hilo.hi,
      opMth  -> stage.bits.a
    )
  )
  // IO
  io.out.valid              := stage.valid
  io.out.bits.robIndex      := stage.bits.robIndex
  io.out.bits.issueIndex    := stage.bits.issueIndex
  io.out.bits.regWB.phyReg  := stage.bits.phyRegDst
  io.out.bits.regWB.wen     := regDstWrite
  io.out.bits.regWB.data    := resLo
  io.out.bits.hiloWB.phyReg := stage.bits.hiloPhyRegDst
  io.out.bits.hiloWB.wen    := hiloRegWrite
  io.out.bits.hiloWB.data   := HiLoRegPack(resHi, resLo)

  stage.ready := true.B
}

object LongIntExecUnit {
  val LongIntMult  = 0.U(4.W)
  val LongIntDiv   = 1.U(4.W)
  val LongIntMultu = 2.U(4.W)
  val LongIntDivu  = 3.U(4.W)

  val LongIntMFL = 4.U(4.W)
  val LongIntMFH = 5.U(4.W)
  val LongIntMTL = 6.U(4.W)
  val LongIntMTH = 7.U(4.W)
}
