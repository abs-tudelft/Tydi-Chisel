package tydi_lib

import chisel3.Data

abstract class TranspileExtend {
  def transpile(map: Map[String, String]): Map[String, String]
  def tydiCode: String
  def fingerprint: String
}

object ReverseTranspiler {
  implicit class StreamTranspile(e: PhysicalStream) extends TranspileExtend {
    def tydiCode: String = {
      var str = s"${e.instanceName} = Stream(${e.elementType.instanceName}, t=${e.n}, d=${e.d}, c=${e.c})"
      str
    }

    def transpile(map: Map[String, String]): Map[String, String] = {
      var m = map
      m = e.elementType.transpile(m)
      val s = fingerprint
      if (m.contains(s)) return m
      m += (fingerprint -> tydiCode)
      m
    }

    def fingerprint: String = e.instanceName
  }

  implicit class GroupTranspile(e: Group) extends TranspileExtend {
    def tydiCode: String = {
      var str = s"Group ${e.instanceName} {\n"
      for ((elName, elData) <- e.elements) {
        str += s"    ${elName}: ${};"
      }
      str
    }

    def transpile(map: Map[String, String]): Map[String, String] = {
      var m = map
      // Add all group elements to the map
      for (el <- e.getElements) {
        m = el.transpile(m)
      }
      val s = fingerprint
      if (map.contains(s)) return m
      m += (fingerprint -> tydiCode)
      m
    }

    def fingerprint: String = e.instanceName
  }

  implicit class BitsTranspile(e: Data) extends TranspileExtend {
    def tydiCode: String = {
      var str = s"${e.instanceName} = Bit(${e.getWidth}); // ${fingerprint}"
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