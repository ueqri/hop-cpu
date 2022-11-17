package core.rob

import chisel3._

import core.CoreParameter
import core.MircoInstruction

class ROBCtrlBundle(implicit p: CoreParameter) extends Bundle {
  val valid = Bool()

  val regDstLogic = UInt(5.W)
  val regDst      = UInt(p.PhyRegBits.W)
  val regDstOld   = UInt(p.PhyRegBits.W)
  val regDstWrite = Bool()

  val writeHiLo  = Bool()
  val regHilo    = UInt(p.PhyHiLoRegBits.W)
  val regHiloOld = UInt(p.PhyHiLoRegBits.W)

  val pc         = UInt(32.W)
  val branchMask = UInt(p.BranchIndexNum.W)

  val isBranch      = Bool()
  val branchIndexOH = UInt(p.BranchIndexNum.W)
  val isStore       = Bool()

  val syscall = Bool()
  val eret    = Bool()
  val break   = Bool()

  val execDelaySlot = Bool()
}

object ROBCtrlBundle {
  def apply(ins: MircoInstruction)(implicit p: CoreParameter) = {
    val w = Wire(new ROBCtrlBundle)

    w.valid         := ins.valid
    w.isStore       := ins.isStore
    w.isBranch      := ins.isBranch
    w.branchIndexOH := ins.branchIndexOH
    w.branchMask    := ins.branchMask
    w.regDst        := ins.regDst
    w.regDstLogic   := ins.regDstLogic
    w.regDstOld     := ins.regDstOld
    w.regDstWrite   := ins.regDstWrite
    w.writeHiLo     := ins.writeHiLo
    w.regHilo       := ins.hiLoReg
    w.regHiloOld    := ins.hiLoRegOld
    w.pc            := ins.pc
    w.execDelaySlot := ins.execDelaySlot
    w.syscall       := ins.syscall
    w.eret          := ins.eret
    w.break         := ins.break

    w.asTypeOf(w)
  }
}
