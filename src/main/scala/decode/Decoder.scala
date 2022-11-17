package core.decode

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import core.CoreParameter
import core.MircoInstruction
import core.Instructions._
import core.MircoInstruction._
import core.shortint.ShortIntUnit._
import core.BranchUnit._
import core.lsu.LoadStoreUnit._
import core.LongIntExecUnit._
import core.cp0.CP0._
import core.rob.CommitBundle

import scala.language.implicitConversions

class Deocder(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val binary = Input(UInt(32.W))
    val pc     = Input(UInt(32.W))

    val instruction = Output(new MircoInstruction)
  })
  val ins = io.instruction

  val X = BitPat("b?")
  val Y = BitPat("b1")
  val N = BitPat("b0")

  val OPX = BitPat("b????")

  val rtAsDst     = Wire(Bool())
  val immSignExt  = Wire(Bool())
  val reg0Req     = Wire(Bool())
  val reg1Req     = Wire(Bool())
  val regDstWrite = Wire(Bool())
  val link        = Wire(Bool()) // if write pc + 8 to R[31]

  val tableDefault = List(N, N, N, N, X, X, N, X, X, X, X, X, X, N, X, Y, N, N, N, UnitXXXXXX, OPX)
  val wires = List(
    ins.isBranch,
    ins.isDircetJump,
    link,
    ins.execDelaySlot,
    reg0Req,
    reg1Req,
    regDstWrite,
    rtAsDst,
    immSignExt,
    ins.immOpr,
    ins.isLoad,
    ins.isStore,
    ins.reqHiLo,
    ins.writeHiLo,
    ins.isBarrier,
    ins.autoComplete,
    ins.syscall,
    ins.eret,
    ins.break,
    ins.exeUnit,
    ins.op
  )

  implicit def UInt2BitPat(x: UInt): BitPat = BitPat(x)

  // TODO: add more instruction
  val table: Array[(BitPat, List[BitPat])] = Array(
    ADD     -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpAdd), // R-Type Start
    SUB     -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSub),
    SLT     -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSlt),
    ADDU    -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpAddu),
    SUBU    -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSubu),
    DIV     -> List(N, N, N, N, Y, Y, N, N, X, X, N, N, N, Y, N, N, N, N, N, UnitLongInt, LongIntDiv),
    DIVU    -> List(N, N, N, N, Y, Y, N, N, X, X, N, N, N, Y, N, N, N, N, N, UnitLongInt, LongIntDivu),
    MULT    -> List(N, N, N, N, Y, Y, N, N, X, X, N, N, N, Y, N, N, N, N, N, UnitLongInt, LongIntMult),
    MULTU   -> List(N, N, N, N, Y, Y, N, N, X, X, N, N, N, Y, N, N, N, N, N, UnitLongInt, LongIntMultu),
    MFHI    -> List(N, N, N, N, N, N, Y, N, X, X, N, N, Y, N, N, N, N, N, N, UnitLongInt, LongIntMFH),
    MFLO    -> List(N, N, N, N, N, N, Y, N, X, X, N, N, Y, N, N, N, N, N, N, UnitLongInt, LongIntMFL),
    MTHI    -> List(N, N, N, N, Y, N, N, N, X, X, N, N, Y, Y, N, N, N, N, N, UnitLongInt, LongIntMTH),
    MTLO    -> List(N, N, N, N, Y, N, N, N, X, X, N, N, Y, Y, N, N, N, N, N, UnitLongInt, LongIntMTL),
    SLTU    -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSltu),
    AND     -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpAnd),
    OR      -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpOrr),
    XOR     -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpXor),
    NOR     -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpNor),
    SLL     -> List(N, N, N, N, N, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSll),
    SRL     -> List(N, N, N, N, N, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSrl),
    SRA     -> List(N, N, N, N, N, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSra),
    SLLV    -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSll),
    SRLV    -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSrl),
    SRAV    -> List(N, N, N, N, Y, Y, Y, N, X, N, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSra),
    ADDI    -> List(N, N, N, N, Y, N, Y, Y, Y, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpAdd), // I-Type Start
    ADDIU   -> List(N, N, N, N, Y, N, Y, Y, Y, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpAddu),
    SLTI    -> List(N, N, N, N, Y, N, Y, Y, Y, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSlt),
    SLTIU   -> List(N, N, N, N, Y, N, Y, Y, Y, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpSltu),
    ANDI    -> List(N, N, N, N, Y, N, Y, Y, N, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpAnd),
    ORI     -> List(N, N, N, N, Y, N, Y, Y, N, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpOrr),
    XORI    -> List(N, N, N, N, Y, N, Y, Y, N, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpXor),
    LUI     -> List(N, N, N, N, N, N, Y, Y, N, Y, N, N, N, N, N, N, N, N, N, UnitShortInt, ShortIntOpLui),
    SW      -> List(N, N, N, N, Y, Y, N, X, Y, X, N, Y, N, N, N, N, N, N, N, UnitLoadSt, LSUOpSw),
    SB      -> List(N, N, N, N, Y, Y, N, X, Y, X, N, Y, N, N, N, N, N, N, N, UnitLoadSt, LSUOpSb),
    SH      -> List(N, N, N, N, Y, Y, N, X, Y, X, N, Y, N, N, N, N, N, N, N, UnitLoadSt, LSUOpSh),
    LW      -> List(N, N, N, N, Y, N, Y, Y, Y, X, Y, N, N, N, N, N, N, N, N, UnitLoadSt, LSUOpLw),
    LB      -> List(N, N, N, N, Y, N, Y, Y, Y, X, Y, N, N, N, N, N, N, N, N, UnitLoadSt, LSUOpLb),
    LBU     -> List(N, N, N, N, Y, N, Y, Y, Y, X, Y, N, N, N, N, N, N, N, N, UnitLoadSt, LSUOpLbu),
    LH      -> List(N, N, N, N, Y, N, Y, Y, Y, X, Y, N, N, N, N, N, N, N, N, UnitLoadSt, LSUOpLh),
    LHU     -> List(N, N, N, N, Y, N, Y, Y, Y, X, Y, N, N, N, N, N, N, N, N, UnitLoadSt, LSUOpLhu),
    BEQ     -> List(Y, N, N, Y, Y, Y, N, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBeq),
    BNE     -> List(Y, N, N, Y, Y, Y, N, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBneq),
    BLEZ    -> List(Y, N, N, Y, Y, N, N, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBlez),
    BLTZ    -> List(Y, N, N, Y, Y, N, N, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBltz),
    BLTZAL  -> List(Y, N, Y, Y, Y, N, Y, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBltz),
    BGEZ    -> List(Y, N, N, Y, Y, N, N, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBgez),
    BGEZAL  -> List(Y, N, Y, Y, Y, N, Y, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBgez),
    BGTZ    -> List(Y, N, N, Y, Y, N, N, X, Y, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPBgtz),
    JR      -> List(Y, N, N, Y, Y, N, N, X, X, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPJr), // J-Type Start
    JRAL    -> List(Y, N, Y, Y, Y, N, Y, X, X, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPJr),
    JAL     -> List(N, Y, Y, Y, N, N, Y, X, X, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPJmp),
    J       -> List(Y, Y, N, Y, N, N, N, X, X, X, N, N, N, N, N, N, N, N, N, UnitBranch, BranchUnitOPJmp),
    MFC0    -> List(N, N, N, N, N, N, Y, Y, X, X, N, N, N, N, Y, N, N, N, N, UnitCP0, CP0Mfc0),
    MTC0    -> List(N, N, N, N, N, Y, N, N, X, X, N, N, N, N, Y, N, N, N, N, UnitCP0, CP0Mtc0),
    SYSCALL -> List(N, N, N, N, N, N, N, N, X, X, N, N, N, N, N, Y, Y, N, N, UnitXXXXXX, OPX),
    ERET    -> List(N, N, N, N, N, N, N, N, X, X, N, N, N, N, N, Y, N, Y, N, UnitXXXXXX, OPX),
    BREAK   -> List(N, N, N, N, N, N, N, N, X, X, N, N, N, N, N, Y, N, N, Y, UnitXXXXXX, OPX)
  )

  require(wires.length == table.head._2.length, "Decode table length mismatch")
  require(wires.length == tableDefault.length, "Decode defalut table length mismatch")

  io.instruction.pc := io.pc

  for ((w, i) <- wires.zipWithIndex) {
    val tab     = table.map(e => e._1 -> e._2(i))
    val default = tableDefault(i)

    w := decoder(QMCMinimizer, io.binary, TruthTable(tab, default))
  }

  val rs      = io.binary(25, 21)
  val rt      = io.binary(20, 16)
  val rd      = io.binary(15, 11)
  val imm     = io.binary(15, 0)
  val jmpAddr = Cat((io.pc + 4.U)(31, 28), io.binary(25, 0), 0.U(2.W))

  ins.valid       := decoder(QMCMinimizer, io.binary, TruthTable(table.map(_._1 -> Y), N))
  ins.reg0        := rs
  ins.reg0Req     := reg0Req & ins.reg0.orR
  ins.reg1        := rt
  ins.reg1Req     := reg1Req & ins.reg1.orR
  ins.regDstLogic := Mux(link, 31.U, Mux(rtAsDst, rt, rd))
  ins.regDstWrite := regDstWrite & ins.regDstLogic.orR
  ins.imm         := Cat(immSignExt & imm(15), imm)
  ins.shamt       := io.binary(10, 6)
  ins.jumpAddr    := jmpAddr
  ins.isLink      := link

  // wires unused
  ins.branchMask    := DontCare
  ins.branchIndexOH := DontCare
  ins.predictTaken  := false.B // TODO branch prediction

  ins.regDst    := DontCare
  ins.regDstOld := DontCare

  ins.hiLoReg    := DontCare
  ins.hiLoRegOld := DontCare

  ins.robIndex   := DontCare
  ins.issueIndex := DontCare
}
