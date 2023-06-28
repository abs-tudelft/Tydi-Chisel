package tydi_lib

import chisel3.Data
import scala.collection.mutable

object ReverseTranspiler {
  implicit class BitsTranspile(e: Data) extends TranspileExtend {
    def tydiCode: String = {
      e match {
        case el: PhysicalStreamBase => el.tydiCode
        case el: Group => el.tydiCode
        case el: Union => el.tydiCode
        case el: Null => el.tydiCode
        case _ => {
          s"$fingerprint = Bit(${e.getWidth}); // ${e.toString}"
        }
      }
    }

    def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = {
      var m = map
      e match {
        case el: PhysicalStreamBase => el.transpile(m)
        case el: Group => el.transpile(m)
        case el: Union => el.transpile(m)
        case el: Null => el.transpile(m)
        case _ => {
          val s = fingerprint
          if (m.contains(s)) return m
          m += (fingerprint -> tydiCode)
          m
        }
      }
    }

    def fingerprint: String = {
      e match {
        case el: PhysicalStreamBase => el.fingerprint
        case el: Group => el.fingerprint
        case el: Union => el.fingerprint
        case el: Null => el.fingerprint
        case _ => {
          val strRep = e.toString
          val name = strRep.replace("<", "_").replace(">", "_").stripSuffix("_")
          val hash = name.hashCode.toHexString.substring(0, 4)
          s"${name}_$hash"
        }
      }
    }
  }
}