package core.lsu

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.util.Stage

class AddressGeneration(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in = Flipped(DecoupledIO(new LoadStoreUnitOpreationBundle))

    val outStoreBuf = DecoupledIO(new StoreBufferEntry)

    val outStoreResult = DecoupledIO(new LoadStoreUnitResultBundle)

    val outMemLoad = DecoupledIO(new MemLoadBundle)
  })

  val stage = Stage(io.in, io.flush)

  val addrSigned = stage.bits.base.zext + stage.bits.offset.asSInt

  val addr = addrSigned.asUInt(31, 0)
  val data = stage.bits.data

  val halfAddrException = addr(0)
  val wordAddrException = addr(1, 0) =/= 0.U

  val op    = stage.bits.op
  val load  = ~op(3)
  val store = op(3)

  val isStoreHalf = op(1, 0) === 1.U
  val isStoreWord = op(1, 0) === 2.U

  val storeAddrException = (isStoreHalf && halfAddrException) || (isStoreWord && wordAddrException)

  val storeBufferEntry = Wire(chiselTypeOf(io.outStoreBuf.bits))
  storeBufferEntry.robIndex    := stage.bits.robIndex
  storeBufferEntry.branchMask  := stage.bits.branchMask
  storeBufferEntry.addrUpper30 := addr(31, 2)
  storeBufferEntry.strb        := getStrb(addr, op)
  storeBufferEntry.data        := getStoreData(data, op).asTypeOf(storeBufferEntry.data)

  val outStoreResult = Wire(chiselTypeOf(io.outStoreResult.bits))
  outStoreResult.robIndex           := stage.bits.robIndex
  outStoreResult.issueIndex         := stage.bits.issueIndex
  outStoreResult.regWB.phyReg       := DontCare
  outStoreResult.regWB.wen          := false.B
  outStoreResult.regWB.data         := DontCare
  outStoreResult.addrExceptionLoad  := false.B
  outStoreResult.addrExceptionStore := storeAddrException
  outStoreResult.badVirtualAddr     := addr

  val memLoadBundle = Wire(chiselTypeOf(io.outMemLoad.bits))
  memLoadBundle.robIndex   := stage.bits.robIndex
  memLoadBundle.issueIndex := stage.bits.issueIndex
  memLoadBundle.phyRegDst  := stage.bits.phyRegDst
  memLoadBundle.op         := stage.bits.op
  memLoadBundle.addr       := addr
  memLoadBundle.data       := DontCare

  // IO
  io.outStoreBuf.valid    := stage.valid && store && io.outStoreResult.ready && ~storeAddrException
  io.outStoreBuf.bits     := storeBufferEntry
  io.outStoreResult.valid := stage.valid && store && io.outStoreBuf.ready
  io.outStoreResult.bits  := outStoreResult

  io.outMemLoad.valid := load && stage.valid
  io.outMemLoad.bits  := memLoadBundle

  stage.ready := Mux(store, io.outStoreBuf.ready && io.outStoreResult.ready, io.outMemLoad.ready)
}
