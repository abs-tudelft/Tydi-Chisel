package tydi_lib.utils

import chisel3._
import chisel3.util.{PriorityEncoder, log2Ceil}
import tydi_lib.TydiModule

class LastSeqProcessor(val n: Int, val d: Int) extends TydiModule {
  val prevReduced: UInt = IO(Input(UInt(d.W)))
  val lasts: Vec[UInt] = IO(Input(Vec(n, UInt(d.W))))
  val dataAt: Vec[Bool] = IO(Input(Vec(n, Bool())))

  val outCheck: UInt = IO(Output(UInt(n.W)))
  val outIndex: UInt = IO(Output(UInt(log2Ceil(n).W)))
  val reducedLasts: Vec[UInt] = IO(Output(Vec(n, UInt(d.W))))

  val newSeqIndictators: Vec[Bool] = Wire(Vec(n, Bool()))

  private var prevRed = prevReduced
  for ((last, reduced, newSeq, hasData) <- lasts.lazyZip(reducedLasts).lazyZip(newSeqIndictators).lazyZip(dataAt)) {
    // A new sequence denoted by last values alone is when a new last stops a dimension already stopped by the previous reduction.
    newSeq := ((prevRed & last) > 0.U)
    reduced := Mux(newSeq | hasData, last, prevRed | last)
    prevRed = reduced
  }

  outCheck := newSeqIndictators.asUInt
  private val index: UInt = PriorityEncoder(Seq(false.B) ++ newSeqIndictators ++ Seq(true.B)) - 1.U
  outIndex := index
}

object LastSeqProcessor {
  def apply(lasts: Vec[UInt]): LastSeqProcessor = {
    val mod = Module(new LastSeqProcessor(lasts.length, lasts(0).getWidth))
    mod.lasts := lasts
    mod
  }
}
