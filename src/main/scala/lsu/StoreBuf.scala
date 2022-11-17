package core.lsu

import chisel3._
import chisel3.util._

import core.CoreParameter
import core.SRAMBundle
import core.util.PrintDebugger
import core.util.Stage
import core.lsu.LoadStoreUnit._
import core.rob.CommitBundle

class StoreBuf(implicit p: CoreParameter) extends Module with PrintDebugger {
  val io = IO(new Bundle {
    val flush = Input(Bool())

    val in      = Flipped(DecoupledIO(new StoreBufferEntry))
    val inIndex = Output(UInt(p.StoreBufferIndexBits.W))

    val out = DecoupledIO(new StoreBufferEntry)

    val loadQuery    = Flipped(DecoupledIO(new MemLoadBundle))
    val loadQueryOut = DecoupledIO(new StoreBufQueryBundle)

    val commit = Flipped(ValidIO(new CommitBundle))
  })

  val storeBuffer         = Reg(Vec(p.StoreBufferDepth, new StoreBufferEntry))
  val storeBufferValid    = RegInit(VecInit.fill(p.StoreBufferDepth)(false.B))
  val storeBufferCommited = RegInit(VecInit.fill(p.StoreBufferDepth)(false.B))

  val storeBufferValidOH    = Cat(storeBufferValid.reverse)
  val storeBufferCommitedOH = Cat(storeBufferCommited.reverse)

  val robIndexToStoreIndex      = Reg(Vec(p.ROBDepth, UInt(p.StoreBufferIndexBits.W)))
  val robIndexToStoreIndexValid = RegInit(VecInit.fill(p.ROBDepth)(false.B))

  val inHarzardDectOH = Cat(storeBuffer.map(_.addrUpper30 === io.in.bits.addrUpper30).reverse)
  val inHarzardOH     = inHarzardDectOH & storeBufferValidOH
  val inHarzard       = inHarzardOH.orR
  val inHarzardAllowOH = Cat(
    storeBuffer.map(_.branchMask === io.in.bits.branchMask).reverse
  )
  val inHarzardAllow = (inHarzardOH & inHarzardAllowOH).orR

  val inIndexSelOH = PriorityEncoderOH(~storeBufferValidOH)
  val inIndexOH    = Mux(inHarzard, inHarzardOH, inIndexSelOH)
  val inIndex      = OHToUInt(inIndexOH)

  val outIndexOH = PriorityEncoderOH(storeBufferCommitedOH & storeBufferValidOH)
  val outIndex   = OHToUInt(outIndexOH)

  val inReady  = Mux(inHarzard, inHarzardAllow, inIndexOH.orR)
  val outValid = outIndexOH.orR

  val commitRobIndex = io.commit.bits.robIndex
  val commit         = io.commit.fire && robIndexToStoreIndexValid(commitRobIndex)
  val commitIndexOH  = UIntToOH(robIndexToStoreIndex(commitRobIndex))

  // take store into buf
  val inEntry = io.in.bits
  for ((buf, i) <- storeBuffer.zipWithIndex) {
    when(io.in.fire && inIndexOH(i)) {
      buf.addrUpper30 := inEntry.addrUpper30
      buf.robIndex    := inEntry.robIndex
      buf.branchMask  := inEntry.branchMask

      when(inHarzard) {
        buf.strb := buf.strb | inEntry.strb
        for ((mask, i) <- inEntry.strb.asBools.zipWithIndex) {
          when(mask) {
            buf.data(i) := inEntry.data(i)
          }
        }
      }.otherwise {
        buf.strb := inEntry.strb
        buf.data := inEntry.data
      }
    }
  }

  when(io.flush) {
    for (v <- robIndexToStoreIndexValid)
      v := false.B
  }.elsewhen(io.in.fire) {
    robIndexToStoreIndex(inEntry.robIndex)      := inIndex
    robIndexToStoreIndexValid(inEntry.robIndex) := true.B
  }

  for ((valid, i) <- storeBufferValid.zipWithIndex) {
    when(io.in.fire && inIndexOH(i)) { // input has prio here
      valid := true.B
    }.elsewhen(io.out.fire && outIndexOH(i)) {
      valid := false.B
    }
  }

  for ((commited, i) <- storeBufferCommited.zipWithIndex) {
    when(io.in.fire && inIndexOH(i)) { // no prio here
      commited := false.B
    }.elsewhen(io.out.fire && outIndexOH(i)) {
      commited := false.B
    }

    when(commit && commitIndexOH(i)) { // no prio here
      commited := true.B
    }
  }

  // flush
  when(io.flush) {
    for (i <- 0 until p.StoreBufferDepth) {
      // valid committed valid after flush
      // 0     0        0
      // 0     1        0
      // 1     0        0
      // 1     1        1
      storeBufferValid(i) := storeBufferValid(i) && storeBufferCommited(i)
    }
  }

  io.in.ready := inReady
  io.inIndex  := inIndex

  io.out.valid := outValid
  io.out.bits  := Mux1H(outIndexOH, storeBuffer)

  // Load Query
  val queryStage = Stage(io.loadQuery, io.flush)
  val queryHitOH = Cat(
    VecInit
      .tabulate(p.StoreBufferDepth) { i =>
        queryStage.bits.addr(31, 2) === storeBuffer(i).addrUpper30 &
          storeBufferValid(i)
      }
      .reverse
  )
  val hit      = queryHitOH.orR
  val hitEntry = Mux1H(queryHitOH, storeBuffer)

  io.loadQueryOut.valid     := queryStage.valid
  io.loadQueryOut.bits.data := hitEntry.data.asTypeOf(UInt(32.W))
  io.loadQueryOut.bits.strb := hitEntry.strb
  io.loadQueryOut.bits.hit  := hit
  queryStage.ready          := io.loadQueryOut.ready
}
