package core.rob

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.util.PrintDebugger
import core.rename.CommitPhyRegBundle
import core.cp0.ExceptionBundle

class ReOrderBuf(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val enq             = Flipped(DecoupledIO(new ROBCtrlBundle()))
    val enqAutoComplete = Input(Bool())
    val enqPtr          = Output(UInt(p.ROBEntryIndexBits.W))

    val writeBack = Flipped(Vec(5, ValidIO(new ROBWriteBackBundle))) // TODO: remove fix para 4
    val commit    = ValidIO(new CommitBundle())

    val commitHiloReg = ValidIO(UInt(p.PhyHiLoRegBits.W))
    val freeHiloReg   = ValidIO(UInt(p.PhyHiLoRegBits.W))

    val commitPhyReg = ValidIO(new CommitPhyRegBundle)
    val freePhyReg   = ValidIO(UInt(p.PhyRegBits.W))

    val freeBranchIndex = ValidIO(UInt(p.BranchIndexNum.W))

    val branchRestore = ValidIO(UInt(p.BranchIndexNum.W))
    val pcRedirect    = ValidIO(UInt(32.W))

    val exception        = ValidIO(new ExceptionBundle)
    val exceptionRestore = Output(Bool())
    val exceptionReturn  = Output(Bool())
    val EPC              = Input(UInt(32.W))

    val writeBadVAddr = ValidIO(UInt(32.W))
    val int           = Flipped(ValidIO(UInt(3.W)))

    val flush = Output(Bool())
  })
  val dispatchMask = Wire(Bool())
  val queue        = Module(new ReOrderBufQueue()).io
  io.enq.ready    := queue.enq.ready && dispatchMask
  queue.enq.valid := io.enq.valid && dispatchMask
  queue.enq.bits  := io.enq.bits

  queue.enqAutoComplete := io.enqAutoComplete
  io.enqPtr             := queue.enqPtr

  queue.writeBack <> io.writeBack

  val deqValid  = queue.deq.valid
  val deqFire   = queue.deq.fire
  val deqCtrl   = queue.deq.bits.ctrl
  val deqResult = queue.deq.bits.result

  val normalPCRedirect  = Wire(Bool())
  val doException       = Wire(Bool())
  val doExceptionReturn = Wire(Bool())
  queue.setAllValid := doException || doExceptionReturn

  val sNormal :: sInDelaySlotAfterBranchTaken :: sOneCycleFlush :: sMultiCycleFlush :: Nil = Enum(4)

  val state    = RegInit(sNormal)
  val stateNxt = WireInit(state)
  state := stateNxt

  switch(state) {
    is(sNormal) {
      when(doException || doExceptionReturn) {
        stateNxt := sMultiCycleFlush
      }.elsewhen(normalPCRedirect) {
        when(deqCtrl.execDelaySlot) {
          stateNxt := sInDelaySlotAfterBranchTaken
        }.otherwise {
          stateNxt := sOneCycleFlush
        }
      }
    }

    is(sInDelaySlotAfterBranchTaken) {
      when(queue.deq.valid) {
        when(doException) {
          stateNxt := sMultiCycleFlush
        }.otherwise {
          stateNxt := sOneCycleFlush
        }
      }
    }

    is(sOneCycleFlush) {
      stateNxt := sNormal
    }

    is(sMultiCycleFlush) {
      when(~queue.deq.valid) {
        stateNxt := sNormal
      }
    }
  }

  val inSNormal                      = RegNext(stateNxt === sNormal, init = false.B)
  val inSInDelaySlotAfterBranchTaken = RegNext(stateNxt === sInDelaySlotAfterBranchTaken, init = false.B)
  val inSOneCycleFlush               = RegNext(stateNxt === sOneCycleFlush, init = false.B)
  val inSMultiCycleFlush             = RegNext(stateNxt === sMultiCycleFlush, init = false.B)

  val insInDelaySlot = RegEnable(deqCtrl.execDelaySlot && inSNormal, deqFire)

  val exceptionRestore = RegNext(inSNormal && (doException || doExceptionReturn), init = false.B)

  dispatchMask := inSNormal

  queue.flush     := inSOneCycleFlush
  queue.deq.ready := ((inSNormal || inSInDelaySlotAfterBranchTaken) && ~doException) || inSMultiCycleFlush

  val fetchAddrMisalign = deqCtrl.pc(1, 0) =/= 0.U
  val fetchBadVAddr     = deqCtrl.pc

  normalPCRedirect := deqValid && inSNormal && deqResult.pcRedirect
  // no interruption in delay slot for simple impl
  doException := deqValid && (inSNormal || inSInDelaySlotAfterBranchTaken) &&
    ((io.int.valid && ~insInDelaySlot) || ~deqCtrl.valid || deqCtrl.syscall || deqCtrl.break || deqResult.overflow || deqResult.addrExceptionLoad || deqResult.addrExceptionStore || fetchAddrMisalign)
  doExceptionReturn := deqValid && inSNormal && deqCtrl.eret

  val pcRedirect = normalPCRedirect || doException || doExceptionReturn
  val pcRedirectAddr = Mux(
    doException || doExceptionReturn,
    Mux(deqCtrl.eret, io.EPC, p.ExceptionEntry.U),
    deqResult.pcRedirectAddr
  )

  val prevPCWithDelaySlot = RegEnable(deqCtrl.pc, enable = deqCtrl.execDelaySlot && deqFire)

  val pendingPCRedirect               = RegEnable(pcRedirect, enable = inSNormal)
  val delaySlotAfterBranchTakenCommit = inSInDelaySlotAfterBranchTaken && deqValid
  val firstCycleInMultiCycleFlush     = RegNext(~inSMultiCycleFlush && stateNxt === sMultiCycleFlush, init = false.B)

  // IO
  io.pcRedirect.valid := (pendingPCRedirect && delaySlotAfterBranchTakenCommit) || firstCycleInMultiCycleFlush
  io.pcRedirect.bits  := RegEnable(pcRedirectAddr, enable = pcRedirect)

  io.commitPhyReg.valid    := deqFire && (inSNormal || inSInDelaySlotAfterBranchTaken) && deqCtrl.regDstWrite
  io.commitPhyReg.bits.log := deqCtrl.regDstLogic
  io.commitPhyReg.bits.phy := deqCtrl.regDst
  io.freePhyReg.valid      := deqFire && (inSNormal || inSInDelaySlotAfterBranchTaken || inSMultiCycleFlush) && deqCtrl.regDstWrite
  io.freePhyReg.bits       := Mux(inSMultiCycleFlush, deqCtrl.regDst, deqCtrl.regDstOld)

  io.commitHiloReg.valid := deqFire && (inSNormal || inSInDelaySlotAfterBranchTaken) && deqCtrl.writeHiLo
  io.commitHiloReg.bits  := deqCtrl.regHilo
  io.freeHiloReg.valid   := deqFire && (inSNormal || inSInDelaySlotAfterBranchTaken || inSMultiCycleFlush) && deqCtrl.writeHiLo
  io.freeHiloReg.bits    := Mux(inSMultiCycleFlush, deqCtrl.regHilo, deqCtrl.regHiloOld)

  io.freeBranchIndex := Pipe(deqFire && inSNormal && deqCtrl.isBranch, deqCtrl.branchIndexOH, latency = 1)
  io.branchRestore   := Pipe(deqFire && inSNormal && deqResult.pcRedirect, deqCtrl.branchIndexOH, latency = 0)

  io.exceptionRestore := exceptionRestore

  io.flush := inSOneCycleFlush || firstCycleInMultiCycleFlush || exceptionRestore

  io.commit.valid := deqFire && (inSNormal || inSInDelaySlotAfterBranchTaken)
  io.commit.bits  := queue.deq.bits

  io.exception.bits.epc := Mux(insInDelaySlot, prevPCWithDelaySlot, deqCtrl.pc)
  io.exception.bits.excCode := Mux1H(
    Seq(
      deqResult.overflow                                 -> 0x0c.U,
      deqCtrl.syscall                                    -> 0x08.U,
      deqCtrl.break                                      -> 0x09.U,
      (deqResult.addrExceptionLoad || fetchAddrMisalign) -> 0x04.U,
      deqResult.addrExceptionStore                       -> 0x05.U,
      ~deqCtrl.valid                                     -> 0x0a.U
    )
  )
  io.exception.bits.inDelaySlot := insInDelaySlot
  io.exception.valid            := doException

  io.exceptionReturn := doExceptionReturn

  io.writeBadVAddr.valid := deqValid && (inSNormal || inSInDelaySlotAfterBranchTaken) && (deqResult.addrExceptionLoad || deqResult.addrExceptionStore || fetchAddrMisalign)
  io.writeBadVAddr.bits  := Mux(fetchAddrMisalign, fetchBadVAddr, deqResult.badVirtualAddr)

  // debug
  if (p.DebugPipeViewLog) {
    when(io.commit.fire) {
      dprintln(p"PipeView:retire:0x${Hexadecimal(deqCtrl.pc)}:0x${Hexadecimal(queue.deqPtr)}")
    }
    when(io.flush) {
      dprintln(p"PipeView:oneCycleFlush")
    }
  }
}
