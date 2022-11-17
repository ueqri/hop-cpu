package core.cp0

import chisel3._

class ExceptionBundle extends Bundle {
  val epc         = UInt(32.W)
  val excCode     = UInt(5.W)
  val inDelaySlot = Bool()
}
