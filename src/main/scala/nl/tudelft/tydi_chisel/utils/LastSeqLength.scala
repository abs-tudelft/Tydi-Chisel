package nl.tudelft.tydi_chisel.utils

import chisel3._
import chisel3.util.{PriorityEncoder, log2Ceil}
import nl.tudelft.tydi_chisel.TydiModule

class LastSeqLength(val n: Int, val d: Int) extends TydiModule {
  val lasts = IO(Input(Vec(n, UInt(d.W))))
  val outCheck: UInt = IO(Output(UInt(n.W)))
  val outIndex: UInt = IO(Output(UInt(log2Ceil(n).W)))
  val reducedLasts: Vec[UInt] = VecInit.tabulate(n) { i => lasts.slice(0, i+1).reduce(_ | _) }

  private val check: Vec[Bool] = VecInit(
    // Fixme: The reducedLast should lag 1 behind the last to make this work
    lasts.zip(reducedLasts).map[Bool] { case (last, reducedLast) =>
      (last > 0.U) && (reducedLast >= last)
    }
  )

  outCheck := check.asUInt
  private val index: UInt = PriorityEncoder(Seq(false.B) ++ check ++ Seq(true.B)) - 1.U
  outIndex := index
}

object LastSeqLength {
  def apply(lasts: Vec[UInt]): UInt = {
    val mod = Module(new LastSeqLength(lasts.length, lasts(0).getWidth))
    mod.lasts := lasts
    mod.outIndex
  }
}
