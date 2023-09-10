import chisel3._
import chiseltest._
import chiseltest.experimental.expose
import org.scalatest.flatspec.AnyFlatSpec
import tydi_lib._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import tydi_lib.testing.Conversions._
import tydi_lib.testing.printUtils.{binaryFromUint, printVec, printVecBinary}
import tydi_lib.utils.ComplexityConverter

class TydiComplianceTest extends AnyFlatSpec with ChiselScalatestTester {
  def byteType: UInt = UInt(8.W)

  class DataType extends Group {
    val value1: UInt = byteType
    val value2: UInt = byteType
  }
  val dataType = new DataType()

  class PassThroughModule extends TydiModule {
    val n = 2
    val d = 8
    val c = 8
    val in: PhysicalStreamDetailed[DataType, Null] = IO(Flipped(new PhysicalStreamDetailed(new DataType(), n=n, d=d, c=c).flip))
    val out: PhysicalStreamDetailed[DataType, Null] = IO(new PhysicalStreamDetailed(new DataType(), n=n, d=d, c=c))
    val mid: PhysicalStream = Wire(PhysicalStream(new DataType, n=n, d=d, c=c))
    val mid_out: PhysicalStream = IO(PhysicalStream(new DataType, n=n, d=d, c=c))
    val laneValidityVec: Vec[Bool] = IO(in.laneValidityVec.cloneType)
    laneValidityVec := in.laneValidityVec
    val strbVec: Vec[Bool] = IO(in.strbVec.cloneType)
    strbVec := in.strbVec

    mid := in
    out := mid
    mid_out := mid
  }

  def dataLit(value1: UInt, value2: UInt): DataType = dataType.Lit(_.value1 -> value1, _.value2 -> value2)

  behavior of "TydiComplianceTest"
  // test class body here

  it should "be tydi-compliant" in {
    test(new PassThroughModule()) { c =>
      // Set data values to {00000000, 01010101}, {11111111, 10101010}
      c.in.data.poke(Vec.Lit(dataLit(0.U, 85.U), dataLit(255.U, 170.U)))
      // Set last values to 00000000, 11111111
      c.in.last.poke(Vec.Lit(0.U, 255.U))
      // Strb set second lane valid
      c.in.strb.poke("b10".U)
      c.in.endi.poke(1.U)

      // Least element ends up right in tydi standard.
      // Ordering of items in datatype is not specified, so just keep Chisel's order.
      c.mid_out.data.expect("x0055FFAA".U)
      // Same order for the last signal
      c.mid_out.last.expect("x00FF".U)

      // Check if vectorify functions work well
      c.laneValidityVec.expect(Vec.Lit(0.B, 1.B))
      c.strbVec.expect(Vec.Lit(0.B, 1.B))

      // Check if elements end up at the right place again
      c.out.data.expect(Vec.Lit(dataLit(0.U, 85.U), dataLit(255.U, 170.U)))
      c.out.last.expect(Vec.Lit(0.U, 255.U))
    }
  }

}
