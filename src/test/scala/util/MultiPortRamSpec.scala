package core.util

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._

class MultiPortRamSpec extends AnyFreeSpec with ChiselScalatestTester {
  "MultiPortRam should works fine" in {
    test(new MultiPortRam(UInt(32.W), 512, 2, 2)).withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
      dut =>
        def read(n: Int, addr: Int, expcetData: Int) = {
          dut.io.r(n).addr.poke(addr.U)
          dut.io.r(n).en.poke(true.B)
          dut.clock.step()
          dut.io.r(n).en.poke(false.B)
          dut.io.r(n).data.expect(expcetData.U)
        }

        def write(n: Int, addr: Int, data: Int) = {
          dut.io.w(n).addr.poke(addr.U)
          dut.io.w(n).data.poke(data.U)
          dut.io.w(n).en.poke(true.B)
          dut.clock.step()
          dut.io.w(n).en.poke(false.B)
        }

        fork {
          for (data <- 0 until 128) {
            write(0, data, data)
          }
        }.fork {
          for (data <- 128 until 256) {
            write(1, data, data)
          }
        }.fork {
          for (data <- 0 until 128) {
            read(0, data, data)
          }
        }.fork {
          dut.clock.step()
          for (data <- 128 until 256) {
            read(1, data, data)
          }
        }.join()
    }
  }
}
