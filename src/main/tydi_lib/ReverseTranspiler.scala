package tydi_lib

import chisel3.Data

object ReverseTranspiler {
  implicit class BitsTranspile(e: Data) extends TranspileExtend {
    def tydiCode: String = {
      var str = s"$fingerprint = Bit(${e.getWidth}); // ${e.toString}"
      str
    }

    def transpile(map: Map[String, String]): Map[String, String] = {
      var m = map
      val s = fingerprint
      if (m.contains(s)) return m
      m += (fingerprint -> tydiCode)
      m
    }

    def fingerprint: String = "bits_" + e.toString.hashCode.toHexString
  }
}