package core

import chisel3._

import core.cp0.CP0
import core.rob._
import core.rename._
import core.decode.IDecode
import core.shortint.ShortIntUnit
import core.lsu.LoadStoreUnit

class SRAMBundle extends Bundle {
  val en    = Output(Bool())
  val wen   = Output(UInt(4.W))
  val addr  = Output(UInt(32.W))
  val wdata = Output(UInt(32.W))
  val rdata = Input(UInt(32.W))
}

class DebugBundle extends Bundle {
  val pc      = UInt(32.W)
  val regWen  = UInt(4.W)
  val regNum  = UInt(5.W)
  val regData = UInt(32.W)
}

class Core(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val inst_sram = new SRAMBundle()
    val data_sram = new SRAMBundle()

    val debug = Output(new DebugBundle())
  })
  val iFetch    = Module(new IFetch).io
  val iDecode   = Module(new IDecode).io
  val jumpAhead = Module(new JumpAhead).io
  val iRename   = Module(new IRename).io

  val iDispatchIssue = Module(new IDispatchIssue).io
  val iRegRead       = Module(new RegRead).io
  val shortIntUnit   = Module(new ShortIntUnit).io
  val loadStoreUnit  = Module(new LoadStoreUnit).io
  val branchUnit     = Module(new BranchUnit).io
  val longIntUnit    = Module(new LongIntExecUnit).io

  val rob = Module(new ReOrderBuf).io

  val cp0 = Module(new CP0).io

  val writeBackDebugger = Module(new WriteBackDebugger).io

  iFetch.out    <> iDecode.in
  iDecode.out   <> jumpAhead.in
  jumpAhead.out <> iRename.in
  iRename.out   <> iDispatchIssue.in

  iDispatchIssue.out <> iRegRead.in

  iRegRead.outShortIntUnit    <> shortIntUnit.in
  iRegRead.outLoadStoreUnit   <> loadStoreUnit.in
  iRegRead.outBranchUnit      <> branchUnit.in
  iRegRead.outLongIntExecUnit <> longIntUnit.in
  iRegRead.outCP0             <> cp0.in

  // write back
  def lengthEqu[T1, T2](a: Iterable[T1], b: Iterable[T2]) = a.sizeCompare(b) == 0

  val robWriteBacks = Seq(
    (shortIntUnit.out.fire, ROBWriteBackBundle(shortIntUnit.out.bits)),
    (loadStoreUnit.out.fire, ROBWriteBackBundle(loadStoreUnit.out.bits)),
    (branchUnit.out.fire, ROBWriteBackBundle(branchUnit.out.bits)),
    (longIntUnit.out.fire, ROBWriteBackBundle(longIntUnit.out.bits)),
    (cp0.out.fire, ROBWriteBackBundle(cp0.out.bits))
  )
  require(lengthEqu(robWriteBacks, rob.writeBack))
  for ((wb, (valid, bits)) <- rob.writeBack.zip(robWriteBacks)) {
    wb.valid := valid
    wb.bits  := bits
  }

  val regWriteBacks = Seq(
    (shortIntUnit.out.fire, shortIntUnit.out.bits.regWB),
    (loadStoreUnit.out.fire, loadStoreUnit.out.bits.regWB),
    (branchUnit.out.fire, branchUnit.out.bits.regWB),
    (longIntUnit.out.fire, longIntUnit.out.bits.regWB),
    (cp0.out.fire, cp0.out.bits.regWB)
  )
  require(lengthEqu(regWriteBacks, iRegRead.writeBack))
  for ((wb, (valid, bits)) <- iRegRead.writeBack.zip(regWriteBacks)) {
    wb.valid := valid
    wb.bits  := bits
  }

  iRegRead.hiloWriteBack.valid := longIntUnit.out.fire
  iRegRead.hiloWriteBack.bits  := longIntUnit.out.bits.hiloWB

  val issueWriteBacks = Seq(
    (shortIntUnit.out.fire, shortIntUnit.out.bits.issueIndex),
    (loadStoreUnit.out.fire, loadStoreUnit.out.bits.issueIndex),
    (branchUnit.out.fire, branchUnit.out.bits.issueIndex),
    (longIntUnit.out.fire, longIntUnit.out.bits.issueIndex),
    (cp0.outIssueWindow.fire, cp0.outIssueWindow.bits)
  )
  require(lengthEqu(issueWriteBacks, iDispatchIssue.writeBack))
  for ((wb, (valid, bits)) <- iDispatchIssue.writeBack.zip(issueWriteBacks)) {
    wb.valid := valid
    wb.bits  := bits
  }

  iDecode.freeBranchIndex  := rob.freeBranchIndex
  iRename.commitPhyReg     := rob.commitPhyReg
  iRename.freePhyReg       := rob.freePhyReg
  iRename.commitHiloReg    := rob.commitHiloReg
  iRename.freeHiloReg      := rob.freeHiloReg
  iRename.branchRestore    := rob.branchRestore
  iRename.exceptionRestore := rob.exceptionRestore
  loadStoreUnit.commit     := rob.commit
  cp0.commit               := rob.commit

  cp0.exception       <> rob.exception
  cp0.exceptionReturn := rob.exceptionReturn
  cp0.writeBadVAddr   := rob.writeBadVAddr
  rob.int             := cp0.int
  rob.EPC             := cp0.EPC

  iDispatchIssue.commit.fire := false.B
  iDispatchIssue.commit.bits := rob.commit.bits.robIndex

  iDispatchIssue.robEnq    <> rob.enq
  rob.enqAutoComplete      := iDispatchIssue.robEnqAutoComplete
  iDispatchIssue.robEnqPtr := rob.enqPtr

  io.inst_sram <> iFetch.imem
  io.data_sram <> loadStoreUnit.dmem

  iFetch.pcRedirect.valid := rob.pcRedirect.fire | jumpAhead.pcRedirect.fire
  iFetch.pcRedirect.bits  := Mux(rob.pcRedirect.valid, rob.pcRedirect.bits, jumpAhead.pcRedirect.bits)

  iDecode.flush            := rob.flush | jumpAhead.flushOut
  jumpAhead.flush          := rob.flush
  iDecode.branchIndexFLush := rob.flush
  iRename.flush            := rob.flush
  iDispatchIssue.flush     := rob.flush
  iRegRead.flush           := rob.flush
  shortIntUnit.flush       := rob.flush
  loadStoreUnit.flush      := rob.flush
  branchUnit.flush         := rob.flush
  longIntUnit.flush        := rob.flush
  cp0.flush                := rob.flush

  writeBackDebugger.commit := rob.commit
  io.debug                 := writeBackDebugger.debug
}
