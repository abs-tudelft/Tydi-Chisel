package nl.tudelft.tydi_chisel.utils

import chisel3._
import nl.tudelft.tydi_chisel._

class StreamHold(streamTemplate: PhysicalStream) extends SubProcessorSignalDef {
  val out: PhysicalStream = IO(streamTemplate)
  val in: PhysicalStream  = IO(Flipped(streamTemplate))

  private val dataReg: UInt = Reg(UInt(streamTemplate.elWidth.W))
  private val lastReg: UInt = Reg(UInt(streamTemplate.lastWidth.W))
  private val strbReg: UInt = Reg(UInt(streamTemplate.n.W))
  private val staiReg: UInt = Reg(UInt(streamTemplate.stai.getWidth.W))
  private val endiReg: UInt = Reg(UInt(streamTemplate.endi.getWidth.W))
  private val isFull: Bool  = RegInit(false.B)

  in.ready := !isFull
  when(in.valid && !isFull) {
    dataReg := in.data
    lastReg := in.last
    strbReg := in.strb
    staiReg := in.stai
    endiReg := in.endi
    isFull  := true.B
  }

  out.valid := isFull
  when(out.ready && isFull) {
    out.data := dataReg
    out.last := lastReg
    out.strb := strbReg
    out.stai := staiReg
    out.endi := endiReg
    isFull   := false.B
  }
}
