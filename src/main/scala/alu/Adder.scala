package core.alu

import chisel3._

class Adder extends Module {
  val io = IO {
    new Bundle {
      val a   = Input(UInt(32.W))
      val b   = Input(UInt(32.W))
      val cin = Input(UInt(1.W))

      val s    = Output(UInt(32.W))
      val cout = Output(UInt(1.W))
    }
  }

  val s = io.a + io.b + io.cin
}

class ALU extends Module {
  val io = IO {
    new Bundle {
      val sub = Input(Bool())

      val a = Input(UInt(32.W))
      val b = Input(UInt(32.W))

      val s  = Output(UInt(32.W))
      val eq = Output(Bool())
    }
  }

  val adder = Module(new Adder).io
  adder.a   := io.a
  adder.b   := Mux(io.sub, ~io.b, io.b)
  adder.cin := io.sub

  io.eq := io.a === io.b
}
