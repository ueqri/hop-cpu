package core.rob

import chisel3._

import core.CoreParameter

class ROBResultBundle(implicit p: CoreParameter) extends Bundle {
  // for debug only
  val data = UInt(32.W)

  val pcRedirectAddr = UInt(32.W) // TODO: let rob read it from other places
  val pcRedirect     = Bool()

  val overflow           = Bool()
  val addrExceptionLoad  = Bool()
  val addrExceptionStore = Bool()
  val badVirtualAddr     = UInt(32.W) // TODO: let rob read it from other places
}
