package core.util

import chisel3._
import chisel3.util._

object Stage {
  def apply[T <: Data](in: DecoupledIO[T], flush: Bool) = {
    val stage      = Wire(DecoupledIO(chiselTypeOf(in.bits)))
    val stageValid = RegInit(false.B)

    when(flush) {
      stageValid := false.B
    }.elsewhen(in.ready) {
      stageValid := in.valid
    }

    stage.valid := stageValid
    stage.bits  := RegEnable(in.bits, in.fire)

    in.ready := stage.ready || ~stage.valid

    stage
  }
}
