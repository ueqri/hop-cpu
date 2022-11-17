package core.util

import chisel3._

trait PrintDebugger {
  private val clockCnt = RegInit(1.U(32.W)).suggestName("DebugClockCounter")
  clockCnt := clockCnt + 1.U

  def dprintln(p: Printable) = {
    dprint(p + "\n")
  }

  def dprint(p: Printable) = {
    printf(p"${clockCnt}:" + p)
  }
}
