package core.axi

import chisel3._
import chisel3.util.log2Ceil
import chisel3.util.DecoupledIO

class AXIAddressChannel(val addrWidth: Int) extends Bundle {
  val id = UInt(4.W)
  val addr = UInt(addrWidth.W)
  val len = UInt(2.W)
  val size = UInt(2.W)
  val burst = UInt(2.W)
}

class AXIReadDataChannel(val dataWidth: Int) extends Bundle {
  val id = UInt(4.W)
  val data = UInt(dataWidth.W)
  val resp = UInt(2.W)
  val last = Bool()
}

class AXIWriteDataChannel(val dataWidth: Int) extends Bundle {
  val id = UInt(4.W)
  val data = UInt(dataWidth.W)
  val strb = UInt(log2Ceil(dataWidth).W)
  val last = Bool()
}

class AXIWriteResponseChannel(val dataWidth: Int) extends Bundle {
  val id = UInt(4.W)
  val resp = UInt(2.W)
}

class AXI4MMIO(val dataWidth: Int, val addrWidth: Int) extends Bundle {
  val aw = Flipped(DecoupledIO(new AXIAddressChannel(addrWidth)))
  val w = Flipped(DecoupledIO(new AXIAddressChannel(addrWidth)))
  val b = DecoupledIO(new AXIAddressChannel(addrWidth))

  val ar = Flipped(DecoupledIO(new AXIAddressChannel(addrWidth)))
  val r = DecoupledIO(new AXIReadDataChannel(addrWidth))
}
