package tydi_lib

import chisel3.Data

object ReverseTranspiler {
  implicit class BitsTranspile(e: Data) extends TranspileExtend {
    def tydiCode: String = {
      val name = e.getClass.getName
      var str = s"$name = Bit(${e.getWidth}); // $fingerprint"
      str
    }

    def transpile(map: Map[String, String]): Map[String, String] = {
      var m = map
      val s = fingerprint
      if (m.contains(s)) return m
      m += (fingerprint -> tydiCode)
      m
    }

    def fingerprint: String = e.toString
  }
}