package core.cp0

import chisel3._
import chisel3.util._

import core.CoreParameter

class CP0Regfiles(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val regRW = new CP0RegfilesRegRWBundle

    val exception       = Flipped(ValidIO(new ExceptionBundle))
    val exceptionReturn = Input(Bool())

    val writeBadVAddr = Flipped(ValidIO(UInt(32.W)))

    val intPending = Output(UInt(8.W))
    val intMask    = Output(UInt(8.W))
    val intEnable  = Output(Bool())
    val EXL        = Output(Bool())
    val EPC        = Output(UInt(32.W))

  })

  // ctrl
  val waddrOH = UIntToOH(io.regRW.waddr)
  val wen     = io.regRW.wen

  // data path
  val badVAddr = Reg(UInt(32.W))
  when(io.writeBadVAddr.fire) {
    badVAddr := io.writeBadVAddr.bits
  }.elsewhen(wen && waddrOH(8)) {
    badVAddr := io.regRW.wdata
  }

  val count    = Reg(UInt(32.W))
  val countInc = RegInit(false.B)
  countInc := ~countInc

  when(wen && waddrOH(9)) {
    count    := io.regRW.wdata
    countInc := false.B
  }.elsewhen(countInc) {
    count := count +& 1.U
  }

  val bev     = 1.U(1.W)
  val intMask = Reg(UInt(8.W))
  val exl     = RegInit(0.U(1.W))
  val intEn   = RegInit(0.U(1.W))
  val status  = Cat(0.U(9.W), bev, 0.U(6.W), intMask, 0.U(6.W), exl, intEn)
  assert(status.getWidth == 32)
  when(wen && waddrOH(12)) {
    intMask := io.regRW.wdata(15, 8)
    intEn   := io.regRW.wdata(0)
  }

  when(io.exception.fire) {
    exl := true.B
  }.elsewhen(io.exceptionReturn) {
    exl := false.B
  }.elsewhen(wen && waddrOH(12)) {
    exl := io.regRW.wdata(1)
  }

  val BD                 = RegInit(0.U(1.W))
  val TI                 = RegInit(0.U(1.W))
  val hardwareIntPending = RegInit(0.U(6.W))
  val softwareIntPending = RegInit(0.U(2.W))
  val excCode            = Reg(UInt(5.W))
  val cause              = Cat(BD, TI, 0.U(14.W), hardwareIntPending, softwareIntPending, 0.U(1.W), excCode, 0.U(2.W))
  assert(cause.getWidth == 32)
  when(wen && waddrOH(13)) {
    softwareIntPending := io.regRW.wdata(9, 8)
  }
  when(io.exception.fire) {
    excCode := io.exception.bits.excCode
  }
  when(io.exception.fire && ~exl.asBool) {
    BD := io.exception.bits.inDelaySlot
  }

  val epc = Reg(UInt(32.W))
  when(io.exception.fire && ~exl.asBool) {
    epc := io.exception.bits.epc
  }.elsewhen(wen && waddrOH(14)) {
    epc := io.regRW.wdata
  }

  // io
  io.regRW.rdata := MuxLookup(
    io.regRW.raddr,
    DontCare,
    Seq(
      8.U  -> badVAddr,
      9.U  -> count,
      12.U -> status,
      13.U -> cause,
      14.U -> epc
    )
  )

  io.intPending := Cat(hardwareIntPending, softwareIntPending)
  io.intMask    := intMask
  io.intEnable  := intEn
  io.EXL        := exl
  io.EPC        := epc
}
