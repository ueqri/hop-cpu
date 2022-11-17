package core.lsu

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.SRAMBundle
import core.MircoInstruction.UnitOpBits
import core.lsu.LoadStoreUnit._
import core.util.Stage
import core.rob.CommitBundle
import core.util._

class LoadStoreUnit(implicit p: CoreParameter) extends Module {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in  = Flipped(DecoupledIO(new LoadStoreUnitOpreationBundle))
    val out = ValidIO(new LoadStoreUnitResultBundle)

    val commit = Flipped(ValidIO(new CommitBundle))

    val dmem = new SRAMBundle()
  })
  val ag         = Module(new AddressGeneration)
  val storeBuf   = Module(new StoreBuf)
  val sramCtrl   = Module(new SramCtrl)
  val loadUnit   = Module(new LoadUnit)
  val outArbiter = Module(new RRArbiter(chiselTypeOf(io.out.bits), 2))

  io.in <> ag.io.in

  ag.io.outStoreBuf    <> storeBuf.io.in
  ag.io.outStoreResult <> outArbiter.io.in(0)

  storeBuf.io.commit := io.commit

  sramCtrl.io.inStore <> DecoupledMap(
    storeBuf.io.out,
    { entry: StoreBufferEntry =>
      val x = Wire(new MemoryStoreBundle)

      x.addr := Cat(entry.addrUpper30, 0.U(2.W))
      x.data := entry.data.asTypeOf(UInt(32.W))
      x.strb := entry.strb

      x
    }
  )

  val memLoads = DecoupledSimpleScatter(ag.io.outMemLoad, 2)
  memLoads(0) <> sramCtrl.io.inLoad
  memLoads(1) <> storeBuf.io.loadQuery

  storeBuf.io.loadQueryOut  <> loadUnit.io.inStoreBufQuery
  sramCtrl.io.outLoad       <> loadUnit.io.inLoad
  loadUnit.io.outLoadResult <> outArbiter.io.in(1)

  io.out.valid            := outArbiter.io.out.valid
  io.out.bits             := outArbiter.io.out.bits
  outArbiter.io.out.ready := true.B

  ag.io.flush       := io.flush
  storeBuf.io.flush := io.flush
  sramCtrl.io.flush := io.flush
  loadUnit.io.flush := io.flush

  sramCtrl.io.dmem <> io.dmem
}

object LoadStoreUnit {
  val LSUOpLb  = "b0000".U(UnitOpBits.W)
  val LSUOpLh  = "b0001".U(UnitOpBits.W)
  val LSUOpLw  = "b0010".U(UnitOpBits.W)
  val LSUOpLbu = "b0100".U(UnitOpBits.W)
  val LSUOpLhu = "b0101".U(UnitOpBits.W)
  val LSUOpSb  = "b1000".U(UnitOpBits.W)
  val LSUOpSh  = "b1001".U(UnitOpBits.W)
  val LSUOpSw  = "b1010".U(UnitOpBits.W)
}

object getStoreData {
  def apply(data: UInt, op: UInt) = {
    val storeByteData = Cat(Seq.fill(4) { data(7, 0) })
    val storeHalfData = Cat(data(15, 0), data(15, 0))
    val res = MuxLookup(
      op(1, 0),
      DontCare,
      Seq(
        0.U -> storeByteData,
        1.U -> storeHalfData,
        2.U -> data
      )
    )

    res
  }
}

object getStrb {
  def apply(addr: UInt, op: UInt) = {
    require(addr.getWidth == 32)
    require(op.getWidth == UnitOpBits)

    val byteStrb = UIntToOH(addr(1, 0))
    val halfSel  = UIntToOH(addr(1))
    val halfStrb = Cat(halfSel(1), halfSel(1), halfSel(0), halfSel(0))
    val wordStrb = 0xf.U(4.W)

    val isStoreByte = op(1, 0) === 0.U
    val isStoreHalf = op(1, 0) === 1.U
    val isStoreWord = op(1, 0) === 2.U

    val strb = MuxCase(
      0.U,
      Seq(
        isStoreByte -> byteStrb,
        isStoreHalf -> halfStrb,
        isStoreWord -> wordStrb
      )
    )

    strb
  }
}
