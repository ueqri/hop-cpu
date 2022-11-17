package core.util

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.VecLiterals._
import chisel3.experimental.BundleLiterals._

import MultiPortQueueTestHelperObj._

class MultiPortQueueSpec extends AnyFreeSpec with ChiselScalatestTester {
"MultiPortQueue should works fine" in {
  test(new MultiPortQueue(UInt(32.W), 32, 4))
   .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
     
    val datal = (0 until 128).toSeq.map(_.U)
    fork {
      for(i <- 1 to dut.cols)
        dut.enqueue(datal, 0.U, i)
      for(i <- 1 to dut.cols)
        dut.enqueue(datal, 0.U, i)
      for(i <- dut.cols until(0, -1))
        dut.enqueue(datal, 0.U, i)
    }.fork{
      for(i <- 1 to dut.cols)
        dut.dequeueExpect(datal, i)
      for(i <- dut.cols until(0, -1))
        dut.dequeueExpect(datal, i)
      for(i <- 1 to dut.cols)
        dut.dequeueExpect(datal, i)
    }.join()
    dut.clock.step()
    /*
    var start = 0
    while(start < data.length) {
      val n = (data.length - start) min 4
      val d = data.slice(start, start + n)
      dut.enqueueOne(d.map(_.U), n)
      start += n;
    }
    
    dut.deq.ready.poke(4.U)
    dut.clock.step()
    dut.clock.step(2)
    dut.io.enq.ready.expect(4.U)
    dut.io.enq.data(0).poke(1.U)
    dut.enqueue(Seq(1,2,3,4).map(_.U), 4)
    dut.clock.step(10)
    dut.io.deq.ready.poke(2.U)
    dut.clock.step()
    */
  }
}
}

private object MultiPortQueueTestHelperObj {

implicit class MultiPortQueueTestHelper[T <: Data](dut : MultiPortQueue[T]) {
  val enq = dut.io.enq
  val deq = dut.io.deq
  def enqueue(data : Seq[T], default : T, nenqMax : Int = dut.cols) = {
    var start = 0
    while(start < data.length) {
      timescope {
        val ready = enq.ready.peek().litValue.toInt
        val nenq = (data.length - start) min ready min nenqMax
        if(nenq > 0) {
          val offset = dut.io.enq_offset.peek().litValue.toInt
          val d = data.slice(start, start + nenq).padTo(enq.data.length, default)
          enq.valid.poke(nenq.U)
        
          vecPoke(enq.data, RotateShift.right(d, offset))

          start += nenq
        }
        dut.clock.step()
      }
    }
  }

  def enqueueRnd(data : Seq[T], default : T) = {

  }

  def dequeueExpect(data : Seq[T], ndeqMax : Int = dut.cols) = {
    var start = 0
    val cols = deq.data.length
    while(start < data.length) {
      timescope {
        val valid = deq.valid.peek().litValue.toInt
        val ndeq = (data.length - start) min valid min ndeqMax
        if(ndeq > 0) {
          val offset = dut.io.deq_offset.peek().litValue.toInt
          val d = data.slice(start, start + ndeq)

          deq.ready.poke(ndeq.U)
          for(i <- 0 until ndeq) {
            deq.data((i + offset) % cols).expect(d(i))
          }
          start += ndeq
        }
        dut.clock.step()
      }
    }
  }

  private def vecPoke(vec : Vec[T], data : Seq[T]) = {
    vec zip data foreach { case (v, d) =>
      v.poke(d)
    }
  }

  private def vecExpect(vec : Vec[T], data : Seq[T]) = {
    vec zip data foreach { case (v, d) =>
      v.expect(d)
    }
  }
}
}