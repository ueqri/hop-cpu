package core.shortint

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.MircoInstruction
import core.MircoInstruction._

import core.shortint.ShortIntUnit._
import core.util.Stage
import core.util.DecoupledHalfRegSlice

class ShortIntUnit(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val in    = Flipped(DecoupledIO(new ShortIntUnitOperationBundle()))

    val out = ValidIO(new ShortIntUnitResultBundle())
  })

  val stage = Stage(io.in, io.flush)
  val op    = stage.bits.op
  val a     = stage.bits.a
  val b     = stage.bits.b
  val shamt = stage.bits.shamt

  val add  = a + b
  val sub  = a - b
  val and  = a & b
  val or   = a | b
  val xor  = a ^ b
  val nor  = ~(a | b)
  val slt  = (a.asSInt() < b.asSInt()).asUInt() //Mux[UInt](a.asSInt() < b.asSInt(), 1.U(32.W) , 0.U(32.W))
  val sltu = (a.asUInt() < b.asUInt()).asUInt() // Mux[UInt](a.asUInt() < b.asUInt(), 1.U(32.W) , 0.U(32.W))
  val lui  = Cat(b(15, 0), 0.U(16.W))
  val sll  = b.asUInt() << shamt
  val srl  = (b.asUInt() >> shamt).asUInt
  val sra  = (b.asSInt() >> shamt).asUInt

  //默认值设为全1
  val result = MuxLookup(
    op,
    0.U(32.W),
    Seq(
      ShortIntOpAdd  -> add,
      ShortIntOpAddu -> add,
      ShortIntOpSub  -> sub,
      ShortIntOpSubu -> sub,
      ShortIntOpAnd  -> and,
      ShortIntOpOrr  -> or,
      ShortIntOpXor  -> xor,
      ShortIntOpNor  -> nor,
      ShortIntOpSlt  -> slt,
      ShortIntOpSltu -> sltu,
      ShortIntOpLui  -> lui,
      ShortIntOpSll  -> sll,
      ShortIntOpSrl  -> srl,
      ShortIntOpSra  -> sra
    )
  )

  val aSign      = a(31)
  val bSign      = b(31)
  val resultSign = result(31)

  val addOverflow = op === ShortIntOpAdd && ((aSign && bSign && ~resultSign) || (~aSign && ~bSign && resultSign))
  val subOverflow = op === ShortIntOpSub && ((aSign && ~bSign && ~resultSign) || (~aSign && bSign && resultSign))

  // IO
  io.out.valid             := stage.valid
  io.out.bits.robIndex     := stage.bits.robIndex
  io.out.bits.issueIndex   := stage.bits.issueIndex
  io.out.bits.regWB.phyReg := stage.bits.phyRegDst
  io.out.bits.regWB.wen    := stage.bits.phyRegDst.orR
  io.out.bits.regWB.data   := result
  io.out.bits.overflow     := addOverflow || subOverflow

  stage.ready := true.B
}

object ShortIntUnit {
  val ShortIntOpX = BitPat("b????")

  val ShortIntOpAdd  = 0.U(UnitOpBits.W)
  val ShortIntOpAddu = 1.U(UnitOpBits.W)
  val ShortIntOpSub  = 2.U(UnitOpBits.W)
  val ShortIntOpSubu = 3.U(UnitOpBits.W)
  val ShortIntOpAnd  = 4.U(UnitOpBits.W)
  val ShortIntOpOrr  = 5.U(UnitOpBits.W)
  val ShortIntOpXor  = 6.U(UnitOpBits.W)
  val ShortIntOpNor  = 7.U(UnitOpBits.W)
  val ShortIntOpSlt  = 8.U(UnitOpBits.W)
  val ShortIntOpSltu = 9.U(UnitOpBits.W)
  val ShortIntOpLui  = 10.U(UnitOpBits.W)
  val ShortIntOpSll  = 11.U(UnitOpBits.W)
  val ShortIntOpSrl  = 12.U(UnitOpBits.W)
  val ShortIntOpSra  = 13.U(UnitOpBits.W)
}
