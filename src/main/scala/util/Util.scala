package core.util

import chisel3._
import chisel3.util.Cat

object TreeReduce {
  def apply[T](items: Seq[T], f: (T, T) => T): T = {
    val n = items.length
    n match {
      case 0 => items(-1)
      case 1 => items(0)
      case even if n % 2 == 0 => {
        TreeReduce((0 until (even - 1, 2)).map(i => f(items(i), items(i + 1))), f)
      }
      case odd => {
        TreeReduce((0 until (odd - 1, 2)).map(i => f(items(i), items(i + 1))) ++ Seq(items.last), f)
      }
    }
  }
}

object RotateShift {
  // TODO: impl with barrier shifter
  def left[T <: Data](vec: Vec[T], offset: UInt) = {
    VecInit.tabulate(vec.length) { i =>
      vec(i.U +& offset)
    }
  }

  def right[T <: Data](vec: Vec[T], offset: UInt) = {
    VecInit.tabulate(vec.length) { i =>
      vec(i.U -& offset)
    }
  }

  def left[T](seq: Seq[T], offest: Int) = {
    Seq.tabulate(seq.length) { i =>
      seq((i + offest) % seq.length)
    }
  }

  def right[T](seq: Seq[T], offest: Int) = {
    Seq.tabulate(seq.length) { i =>
      seq((i - offest + seq.length) % seq.length)
    }
  }
}
