package core.util

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._

class TreeReduceSpec extends AnyFreeSpec with ChiselScalatestTester {
"TreeReduce should calculate proper reusult" in {
  assert(TreeReduce[Int](Seq(1,2,3,4), {(a, b) => a + b}) == 10)
  assert(TreeReduce[Int](Seq(1,2,3,4,5), {(a, b) => a + b}) == 15)
}
}

class RotateShiftTest extends Module {
  val io = IO(new Bundle {
    val offset = Input(UInt(2.W))
    val rleft = Output(Vec(4, UInt(32.W)))
    val rright = Output(Vec(4, UInt(32.W)))
  })

  val data = VecInit(0.U, 1.U, 2.U, 3.U)

  io.rleft := RotateShift.left(data, io.offset)
  io.rright := RotateShift.right(data, io.offset)
}

class RotateShiftSpec extends AnyFreeSpec with ChiselScalatestTester {
"Software RotateShift" in {
  val data = (0 until 4).toSeq
  for(i <- 0 until data.length) {
    val rleft = RotateShift.left(data, i)
    val rright = RotateShift.right(data, i)
    for(j <- 0 until data.length) {
      assert(rleft(j) == (j + i) % data.length)
      assert(rright(j) == (j - i + data.length) % data.length)
    }
  }
}


"Hardware RotateShift" in {
  test(new RotateShiftTest) { dut =>
    val data = (0 until 4).toSeq
    for(i <- 0 until 4) {
      val rleft = RotateShift.left(data, i)
      val rright = RotateShift.right(data, i)
      
      dut.io.offset.poke(i.U)
      for(j <- 0 until data.length) {
        dut.io.rleft(j).expect(rleft(j))
        dut.io.rright(j).expect(rright(j))
      }
    }
  }
}

}