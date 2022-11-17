package core.decode

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.MircoInstruction
import core.IFetchOutputBundle
import core.rob.CommitBundle

import core.util.Stage
import core.util.BitAlloctor
import core.util.PrintDebugger

class IDecode(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val flush            = Input(Bool())
    val branchIndexFLush = Input(Bool())

    val in  = Flipped(DecoupledIO(new IFetchOutputBundle))
    val out = DecoupledIO(MircoInstruction())

    val freeBranchIndex = Flipped(ValidIO(UInt(p.BranchIndexNum.W)))
  })

  val branchIndexAlloctor = Module(new BitAlloctor(p.BranchIndexNum))

  val stage = Stage(io.in, io.flush)

  val branchMask = ~branchIndexAlloctor.io.freeBits
  val decode     = Module(new Deocder)
  decode.io.binary := stage.bits.instruction
  decode.io.pc     := stage.bits.pc

  val ins = WireInit(decode.io.instruction)
  ins.branchMask    := branchMask
  ins.branchIndexOH := branchIndexAlloctor.io.out.bits

  val allocBranchIndex     = ins.isBranch && stage.valid
  val allocBranchIndexDone = branchIndexAlloctor.io.out.valid
  val decodeDone           = allocBranchIndexDone || ~allocBranchIndex

  // IO
  stage.ready := io.out.ready && decodeDone

  io.out.valid := stage.valid && decodeDone
  io.out.bits  := ins

  branchIndexAlloctor.io.flush     := io.branchIndexFLush
  branchIndexAlloctor.io.out.ready := allocBranchIndex && stage.ready
  branchIndexAlloctor.io.in.bits   := io.freeBranchIndex.bits
  branchIndexAlloctor.io.in.valid  := io.freeBranchIndex.valid

  // debug
  if (p.DebugPipeViewLog) {
    when(RegNext(io.in.fire && ~io.flush, false.B)) {
      dprintln(p"PipeView:decode:0x${Hexadecimal(stage.bits.pc)}:0x${Hexadecimal(stage.bits.instruction)}")
    }
    when(io.flush && stage.valid) {
      dprintln(p"PipeView:flush:0x${Hexadecimal(stage.bits.pc)}")
    }
  }

  if (p.DebugInvalidInstructionLog)
    when(io.out.fire && ~ins.valid) {
      dprintln(p"Invalid Instruction 0x${Hexadecimal(stage.bits.instruction)} at 0x${Hexadecimal(stage.bits.pc)}")
    }
}
