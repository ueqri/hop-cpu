package core

import chisel3._
import chisel3.util._

import core.MircoInstruction._
import core.cp0.CP0OperationBundle
import core.shortint.ShortIntUnitOperationBundle
import core.lsu.LoadStoreUnitOpreationBundle
import core.util.MultiPortRam
import core.util.Stage
import core.util.PrintDebugger

class HiLoRegPack extends Bundle {
  val hi = UInt(32.W)
  val lo = UInt(32.W)
}

object HiLoRegPack {
  def apply(hi: UInt, lo: UInt) = {
    val w = Wire(new HiLoRegPack())
    w.hi := hi
    w.lo := lo

    w.asTypeOf(w)
  }
}

class PhyRegWriteBundle(implicit p: CoreParameter) extends Bundle {
  val phyReg = UInt(p.PhyRegBits.W)
  val wen    = Bool()
  val data   = UInt(32.W)
}

class PhyHiLoRegWriteBundle(implicit p: CoreParameter) extends Bundle {
  val phyReg = UInt(p.PhyRegBits.W)
  val wen    = Bool()
  val data   = new HiLoRegPack()
}

class RegRead(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in = Flipped(DecoupledIO(MircoInstruction()))

    val outShortIntUnit    = DecoupledIO(new ShortIntUnitOperationBundle)
    val outLoadStoreUnit   = DecoupledIO(new LoadStoreUnitOpreationBundle)
    val outBranchUnit      = DecoupledIO(new BranchUnitOperationBundle)
    val outLongIntExecUnit = DecoupledIO(new LongIntExecUnitOperationBundle)
    val outCP0             = DecoupledIO(new CP0OperationBundle)

    val writeBack     = Vec(5, Flipped(ValidIO(new PhyRegWriteBundle())))
    val hiloWriteBack = Flipped(ValidIO(new PhyHiLoRegWriteBundle()))
  })

  val ram     = MultiPortRam(UInt(32.W), p.PhyRegNum, 5, p.IssueWidth * 2)
  val hiloRam = MultiPortRam(new HiLoRegPack, p.PhyHiLoRegNum, p.IssueWidth, p.IssueWidth)

  ram.io.r(0).addr := io.in.bits.reg0
  ram.io.r(0).en   := io.in.fire
  ram.io.r(1).addr := io.in.bits.reg1
  ram.io.r(1).en   := io.in.fire

  hiloRam.io.r(0).addr := io.in.bits.hiLoReg
  hiloRam.io.r(0).en   := io.in.fire

  val reg0Inited = RegInit(false.B) // assume that no other r/w opreation in the very first cycle after reset
  reg0Inited := true.B

  for ((wb, i) <- io.writeBack.zipWithIndex) {
    if (i != 0) {
      ram.io.w(i).addr := wb.bits.phyReg
      ram.io.w(i).data := wb.bits.data
      ram.io.w(i).en   := wb.bits.wen && wb.fire
    } else {
      ram.io.w(i).addr := Mux(reg0Inited, wb.bits.phyReg, 0.U)
      ram.io.w(i).data := Mux(reg0Inited, wb.bits.data, 0.U)
      ram.io.w(i).en   := ~reg0Inited || (wb.bits.wen && wb.fire)
    }
  }

  val hiloWb = io.hiloWriteBack
  hiloRam.io.w(0).addr := hiloWb.bits.phyReg
  hiloRam.io.w(0).data := hiloWb.bits.data
  hiloRam.io.w(0).en   := hiloWb.bits.wen && hiloWb.fire

  val stage           = Stage(io.in, io.flush)
  val toShortIntUnit  = stage.bits.exeUnit === UnitShortInt
  val toBranchUnit    = stage.bits.exeUnit === UnitBranch
  val toLoadStoreUnit = stage.bits.exeUnit === UnitLoadSt
  val toLongIntUnit   = stage.bits.exeUnit === UnitLongInt
  val toCP0           = stage.bits.exeUnit === UnitCP0

  // IO
  stage.ready := Mux1H(
    Seq(
      toShortIntUnit  -> io.outShortIntUnit.ready,
      toLoadStoreUnit -> io.outLoadStoreUnit.ready,
      toBranchUnit    -> io.outBranchUnit.ready,
      toLongIntUnit   -> io.outLongIntExecUnit.ready,
      toCP0           -> io.outCP0.ready
    )
  )
  io.outShortIntUnit.valid    := stage.valid & toShortIntUnit
  io.outLoadStoreUnit.valid   := stage.valid & toLoadStoreUnit
  io.outBranchUnit.valid      := stage.valid & toBranchUnit
  io.outLongIntExecUnit.valid := stage.valid & toLongIntUnit
  io.outCP0.valid             := stage.valid & toCP0

  val ins       = stage.bits
  val reg0Value = ram.io.r(0).data
  val reg1Value = ram.io.r(1).data
  val imm       = Cat(Cat(Seq.fill(15) { ins.imm(16) }), ins.imm)

  io.outShortIntUnit.bits := ShortIntUnitOperationBundle(
    ins,
    reg0Value,
    Mux(ins.immOpr, imm, reg1Value),
    Mux(ins.reg0Req, reg0Value, ins.shamt)
  )

  io.outBranchUnit.bits    := BranchUnitOperationBundle(ins, reg0Value, reg1Value)
  io.outLoadStoreUnit.bits := LoadStoreUnitOpreationBundle(ins, reg1Value, reg0Value, imm)

  val hilo = hiloRam.io.r(0).data
  io.outLongIntExecUnit.bits := LongIntExecUnitOperationBundle(ins, reg0Value, reg1Value, hilo)

  io.outCP0.bits := CP0OperationBundle(ins, reg1Value)

  // debug
  if (p.DebugPipeViewLog) {
    when(RegNext(io.in.fire && ~io.flush, false.B)) {
      dprintln(p"PipeView:complete:0x${Hexadecimal(stage.bits.pc)}:0x${Hexadecimal(stage.bits.robIndex)}")
    }
    val flushDebugInsPC       = stage.bits.pc
    val flushDebugInsRobInedx = stage.bits.robIndex
    when(io.flush && stage.valid) {
      dprintln(p"PipeView:flush:0x${Hexadecimal(flushDebugInsPC)}:0x${Hexadecimal(flushDebugInsRobInedx)}")
    }
  }
}
