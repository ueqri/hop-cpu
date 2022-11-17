package core.util

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._

class BitAlloctorSpec extends AnyFreeSpec with ChiselScalatestTester {
  "BitAlloctor should works fine" in {
    test(new BitAlloctor(4)).withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) { dut =>
      dut.io.in.initSource()
      dut.io.in.setSourceClock(dut.clock)

      dut.io.out.initSink()
      dut.io.out.setSinkClock(dut.clock)

      dut.io.out.expectDequeue(1.U)
      dut.io.out.expectDequeue(2.U)
      dut.io.out.expectDequeue(4.U)
      dut.io.out.expectDequeue(8.U)

      dut.io.in.enqueue(8.U)
      dut.io.in.enqueue(1.U)
      dut.io.out.expectDequeue(1.U)
      dut.io.out.expectDequeue(8.U)
    }
  }
}
