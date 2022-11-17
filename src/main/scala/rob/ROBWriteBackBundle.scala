package core.rob

import chisel3._

import core.CoreParameter
import core.MircoInstruction
import core.BranchUnitResultBundle
import core.shortint.ShortIntUnitResultBundle
import core.LongIntExecUnitResultBundle
import core.lsu.LoadStoreUnitResultBundle
import core.cp0.CP0ResultBundle

class ROBWriteBackBundle(implicit p: CoreParameter) extends Bundle {
  val robIndex = UInt(p.ROBEntryIndexBits.W)
  val result   = new ROBResultBundle()
}

object ROBWriteBackBundle {
  def apply(x: BranchUnitResultBundle)(implicit p: CoreParameter) = {
    val w = Wire(new ROBWriteBackBundle)
    w.robIndex := x.robIndex

    w.result.data               := x.regWB.data
    w.result.pcRedirect         := x.pcRedirect
    w.result.pcRedirectAddr     := x.pcRedirectAddr
    w.result.overflow           := false.B
    w.result.addrExceptionLoad  := false.B
    w.result.addrExceptionStore := false.B
    w.result.badVirtualAddr     := DontCare

    w.asTypeOf(w)
  }

  def apply(x: ShortIntUnitResultBundle)(implicit p: CoreParameter) = {
    val w = Wire(new ROBWriteBackBundle)
    w.robIndex := x.robIndex

    w.result.data               := x.regWB.data
    w.result.pcRedirect         := false.B
    w.result.pcRedirectAddr     := DontCare
    w.result.overflow           := x.overflow
    w.result.addrExceptionLoad  := false.B
    w.result.addrExceptionStore := false.B
    w.result.badVirtualAddr     := DontCare

    w.asTypeOf(w)
  }

  def apply(x: LoadStoreUnitResultBundle)(implicit p: CoreParameter) = {
    val w = Wire(new ROBWriteBackBundle)
    w.robIndex := x.robIndex

    w.result.data               := x.regWB.data
    w.result.pcRedirect         := false.B
    w.result.pcRedirectAddr     := DontCare
    w.result.overflow           := false.B
    w.result.addrExceptionLoad  := x.addrExceptionLoad
    w.result.addrExceptionStore := x.addrExceptionStore
    w.result.badVirtualAddr     := x.badVirtualAddr

    w.asTypeOf(w)
  }

  def apply(x: LongIntExecUnitResultBundle)(implicit p: CoreParameter) = {
    val w = Wire(new ROBWriteBackBundle)
    w.robIndex := x.robIndex

    w.result.data               := x.regWB.data
    w.result.pcRedirect         := false.B
    w.result.pcRedirectAddr     := DontCare
    w.result.overflow           := false.B
    w.result.addrExceptionLoad  := false.B
    w.result.addrExceptionStore := false.B
    w.result.badVirtualAddr     := DontCare

    w.asTypeOf(w)
  }

  def apply(x: CP0ResultBundle)(implicit p: CoreParameter) = {
    val w = Wire(new ROBWriteBackBundle)
    w.robIndex := x.robIndex

    w.result.data               := x.regWB.data
    w.result.pcRedirect         := false.B
    w.result.pcRedirectAddr     := DontCare
    w.result.overflow           := false.B
    w.result.addrExceptionLoad  := false.B
    w.result.addrExceptionStore := false.B
    w.result.badVirtualAddr     := DontCare

    w.asTypeOf(w)
  }
}
