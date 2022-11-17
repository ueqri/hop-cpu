package core

import chisel3._
import chisel3.util.BitPat

import MircoInstruction._

/**
  * MircoInstruction
  * Binary instruction decoded
  */
class MircoInstruction(implicit p: CoreParameter) extends Bundle {
  // if it is a valid instruction
  val valid = Bool()

  // PC value
  val pc = UInt(32.W)

  // for jump
  val isDircetJump = Bool()
  val jumpAddr     = UInt(32.W)
  val isLink       = Bool()

  // for load store ordering
  val isLoad  = Bool()
  val isStore = Bool()

  // cp0 barrier
  val isBarrier = Bool()

  // mark complete in rob without going through issue and execute unit
  val autoComplete = Bool()

  // Which execute unit is to execute this instruction
  val exeUnit = UInt(ExeUnitBits.W)
  // Execute op
  val op = UInt(4.W)

  // Phy registers
  // but before RegRename Stage, these stores logic reg idx
  val reg0    = UInt(p.PhyRegBits.W)
  val reg0Req = Bool()
  val reg1    = UInt(p.PhyRegBits.W)
  val reg1Req = Bool()
  val imm     = UInt(17.W)
  val immOpr  = Bool()

  val shamt = UInt(5.W)

  val regDstLogic = UInt(5.W)
  val regDst      = UInt(p.PhyRegBits.W)
  val regDstOld   = UInt(p.PhyRegBits.W)
  val regDstWrite = Bool()

  val reqHiLo    = Bool()
  val writeHiLo  = Bool()
  val hiLoReg    = UInt(p.PhyHiLoRegBits.W)
  val hiLoRegOld = UInt(p.PhyHiLoRegBits.W)

  val branchMask = UInt(p.BranchIndexNum.W)

  val syscall = Bool()
  val break   = Bool()
  val eret    = Bool()

  // OOO Info
  val issueIndex = UInt(p.IssueWindowIndexBits.W)
  val robIndex   = UInt(p.ROBEntryIndexBits.W)

  // Prediction Info
  val isBranch      = Bool()
  val execDelaySlot = Bool()
  val branchIndexOH = UInt(p.BranchIndexNum.W)
  val predictTaken  = Bool()
}

object MircoInstruction {
  val ExeUnitBits = 3

  // for exeUnit
  val UnitShortInt = BitPat("b000")
  val UnitBranch   = BitPat("b001")
  val UnitLoadSt   = BitPat("b010")
  val UnitLongInt  = BitPat("b011")
  val UnitCP0      = BitPat("b100")
  val UnitXXXXXX   = BitPat("b???")

  val UnitOpBits = 4

  def apply()(implicit p: CoreParameter) = new MircoInstruction
}
