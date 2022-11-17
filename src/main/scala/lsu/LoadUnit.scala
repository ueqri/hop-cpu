package core.lsu

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.util.Stage
import core.util.DecoupledSimpleMerge

class LoadUnit(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val inStoreBufQuery = Flipped(Decoupled(new StoreBufQueryBundle))
    val inLoad          = Flipped(Decoupled(new MemLoadBundle))

    val outLoadResult = DecoupledIO(new LoadStoreUnitResultBundle)
  })

  val inMerged = DecoupledSimpleMerge(io.inLoad, io.inStoreBufQuery)
  val stage    = Stage(inMerged, io.flush)

  val op   = stage.bits.a.op
  val addr = stage.bits.a.addr

  val storeBufHit  = stage.bits.b.hit
  val storeBufData = stage.bits.b.data.asTypeOf(Vec(4, UInt(8.W)))
  val storeBufStrb = stage.bits.b.strb
  val loadData     = stage.bits.a.data.asTypeOf(Vec(4, UInt(8.W)))
  val dataBytes    = VecInit.tabulate(4) { i => Mux(storeBufHit && storeBufStrb(i), storeBufData(i), loadData(i)) }
  val data         = dataBytes.asTypeOf(UInt(32.W))

  val isLoadByte = op(1, 0) === 0.U
  val isLoadHalf = op(1, 0) === 1.U
  val isLoadWord = op(1, 0) === 2.U
  val signExt    = ~op(2)

  val byteSel = addr(1, 0)
  val byte    = dataBytes(byteSel)
  val byteRes = Cat(Cat(Seq.fill(24) { signExt & byte(7) }), byte)

  val dataHalves        = data.asTypeOf(Vec(2, UInt(16.W)))
  val halfSel           = addr(1)
  val half              = dataHalves(halfSel)
  val halfRes           = Cat(Cat(Seq.fill(16) { signExt & half(15) }), half)
  val halfAddrException = addr(0)

  val wordAddrException = addr(1, 0) =/= 0.U

  val res = MuxCase(
    DontCare,
    Seq(
      isLoadByte -> byteRes,
      isLoadHalf -> halfRes,
      isLoadWord -> data
    )
  )

  val loadException = (isLoadHalf && halfAddrException) || (isLoadWord && wordAddrException)

  // IO
  io.outLoadResult.valid                   := stage.valid
  io.outLoadResult.bits.robIndex           := stage.bits.a.robIndex
  io.outLoadResult.bits.issueIndex         := stage.bits.a.issueIndex
  io.outLoadResult.bits.regWB.phyReg       := stage.bits.a.phyRegDst
  io.outLoadResult.bits.regWB.wen          := true.B
  io.outLoadResult.bits.regWB.data         := res
  io.outLoadResult.bits.addrExceptionLoad  := loadException
  io.outLoadResult.bits.addrExceptionStore := false.B
  io.outLoadResult.bits.badVirtualAddr     := addr

  stage.ready := io.outLoadResult.ready
}
