import chisel3.experimental.BundleLiterals._
import chisel3._
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

val literal = 1.U
val bigLiteral = 1238976875.U(100.W)
val bigInt: BigInt = (1238976875: BigInt) << 65
val bigIntLiteral = bigInt.U(100.W)
bigIntLiteral.litValue
