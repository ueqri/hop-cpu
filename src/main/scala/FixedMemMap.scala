package core

import chisel3._
import chisel3.util.Cat

object FixedMemMap {
  // Addr(31,28)  Addr(31,28) mapped
  // 0000         0000
  // 0001         0001
  // 0010         0010
  // 0011         0011
  // 0100         0100
  // 0101         0101
  // 0110         0110
  // 0111         0111
  // 1000         0000
  // 1001         0001
  // 1010         0000
  // 1011         0001
  // 1100         1100
  // 1101         1101
  // 1110         1110
  // 1111         1111

  def apply(addr: UInt): UInt = {
    require(addr.getWidth == 32)

    val MSNibble     = addr(31, 28)
    val kseg01       = addr(31) & ~addr(30)
    val MSNibbleMask = Cat(~kseg01, ~kseg01, ~kseg01, 1.U(1.W))
    val addrMapped   = Cat(MSNibble & MSNibbleMask, addr(27, 0))

    require(addrMapped.getWidth == 32)
    addrMapped
  }
}
