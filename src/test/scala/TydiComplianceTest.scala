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

  class DataType extends BitsEl(8.W)
  val dataType = new DataType()

  class PassThroughModule extends TydiModule {
    val n = 2
    val d = 8
    val c = 8
    val in: PhysicalStreamDetailed[DataType, Null] = IO(Flipped(new PhysicalStreamDetailed(new DataType(), n=n, d=d, c=c).flip))
    val out: PhysicalStreamDetailed[DataType, Null] = IO(new PhysicalStreamDetailed(new DataType(), n=n, d=d, c=c))
    val mid: PhysicalStream = Wire(PhysicalStream(new DataType, n=n, d=d, c=c))
    val mid_out: PhysicalStream = IO(PhysicalStream(new DataType, n=n, d=d, c=c))

    mid := in
    out := mid
    mid_out := mid
  }

  def dataLit(value: UInt): DataType = dataType.Lit(_.value -> value)

  behavior of "TydiComplianceTest"
  // test class body here

  it should "be tydi-compliant" in {
    test(new PassThroughModule()) { c =>
      c.in.data.poke(Vec.Lit(dataLit(0.U), dataLit(255.U)))
      c.in.last.poke(Vec.Lit(0.U, 255.U))
      c.mid_out.data.expect("x00FF".U)
      c.mid_out.last.expect("x00FF".U)
      c.out.data.expect(Vec.Lit(dataLit(0.U), dataLit(255.U)))
      c.out.last.expect(Vec.Lit(0.U, 255.U))
    }
  }

}
