import chisel3._
import chisel3.experimental.VecLiterals.AddVecLiteralConstructor
import chiseltest._
import chiseltest.experimental.expose
import org.scalatest.flatspec.AnyFlatSpec
import tydi_lib._

class MyEl extends Group {
  val a: UInt = UInt(8.W)
  val b: UInt = UInt(4.W)
}


class BasicTest extends AnyFlatSpec with ChiselScalatestTester {
  def b(num: String): UInt = {
    Integer.parseInt(num, 2).U
  }

  class ComplexityConverterWrapper(template: PhysicalStream, memSize: Int) extends ComplexityConverter(template, memSize) {
    val exposed_currentIndex: UInt = expose(currentIndex)
    val exposed_seriesStored: UInt = expose(seriesStored)
    val exposed_transferLength: UInt = expose(transferLength)
    val exposed_transferCount: UInt = expose(transferCount)
    val exposed_lanes: Vec[UInt] = expose(lanes)
    val exposed_lastLanes: Vec[UInt] = expose(lasts)
    val exposed_storedData: Vec[UInt] = expose(storedData)
    val exposed_storedLasts: Vec[UInt] = expose(storedLasts)
    val exposed_indexes: Vec[UInt] = expose(indexes)
    val exposed_lasts: UInt = expose(leastSignificantLastSignal)
  }

  class ComplexityConverterFancyWrapper(template: PhysicalStream, memSize: Int) extends TydiTestWrapper(new ComplexityConverter(template, memSize))

  behavior of "ComplexityConverter"
  // test class body here

  it should "work with n=1" in {
    val stream = PhysicalStream(new MyEl, n = 1, d = 1, c = 7)

    // test case body here
    test(new ComplexityConverterWrapper(stream, 10)) { c =>
      println("N=1 test")
      // Initialize signals
      println("Initializing signals")
      c.in.last.poke(0.U)
      c.in.strb.poke(1.U)
      c.in.stai.poke(0.U)
      c.in.endi.poke(0.U)
      c.in.valid.poke(false.B)
      c.in.data.poke(555.U)
      c.exposed_currentIndex.expect(0.U) // No items yet
      println(s"Indexes: ${c.exposed_indexes.peek()}")
      println("Step clock")
      c.clock.step()

      c.exposed_currentIndex.expect(0.U) // No items yet

      println("Making data valid")
      // Set some data
      c.in.valid.poke(true.B)
      c.in.data.poke(555.U)
      c.exposed_currentIndex.expect(0.U) // No items yet
      println("Step clock")
      c.clock.step()

      println(s"Data: ${c.exposed_storedData(0).peek()}")
      println(s"Last: ${c.exposed_storedLasts(0).peek()}")
      println(s"Last: ${c.exposed_lasts.peek().litValue.toInt.toBinaryString}")
      c.exposed_currentIndex.expect(1.U)
      c.exposed_transferCount.expect(0.U) // Not transferring yet because output is not ready
      c.exposed_seriesStored.expect(0.U)
      c.in.data.poke(0xABC.U)
      c.in.strb.poke(1.U)
      c.in.last.poke(1.U)
      c.out.valid.expect(0.U) // No full series stored yet
      println("Step clock")
      c.clock.step()

      c.in.last.poke(0.U)
      c.in.valid.poke(false.B)
      c.out.ready.poke(true.B)
      c.exposed_seriesStored.expect(1.U) // One series stored
      c.out.valid.expect(1.U) // ... means valid output
      c.exposed_currentIndex.expect(2.U)
      println(s"Last: ${c.exposed_storedLasts.peek()}")
      println(s"Last: ${c.exposed_lasts.peek().litValue.toInt.toBinaryString}")
      c.exposed_transferLength.expect(1.U)
      println("Step clock")
      c.clock.step()

      println(s"Data: ${c.exposed_storedData.peek()}")
      println(s"Last: ${c.exposed_storedLasts.peek()}")
      println(s"Last: ${c.exposed_lasts.peek().litValue.toInt.toBinaryString}")
      c.exposed_seriesStored.expect(1.U) // Still outputting first series
      c.out.valid.expect(1.U) // ... means valid output
      c.exposed_currentIndex.expect(1.U)
      c.exposed_transferLength.expect(1.U)
      println("N=1 test done")
    }
  }

  it should "work with n=2" in {
    val stream = PhysicalStream(new MyEl, n=2, d=1, c=7)

    // test case body here
    test(new ComplexityConverterWrapper(stream, 10)) { c =>
      println("N=2 test")

      c.in.valid.poke(true.B)
      c.in.data.poke(555.U)
      c.in.strb.poke(1.U)
      c.in.stai.poke(0.U)
      c.in.endi.poke(1.U)
      c.exposed_currentIndex.expect(0.U) // No items yet
      c.clock.step()

      c.exposed_currentIndex.expect(1.U)
      c.in.data.poke(0xABCDEF.U)
      c.in.strb.poke(b("11"))
      c.in.last.poke(b("10"))
      c.clock.step()

      println(s"Data: ${c.exposed_storedData.peek()}")
      println(s"Last: ${c.exposed_storedLasts.peek()}")
      println(s"Last: ${c.exposed_lasts.peek().litValue.toInt.toBinaryString}")
      c.in.last.poke(0.U)
      c.in.valid.poke(false.B)
      c.exposed_currentIndex.expect(3.U)
      c.exposed_seriesStored.expect(1.U)
      c.out.ready.poke(true.B)
      c.exposed_transferLength.expect(2.U)
      c.exposed_transferCount.expect(2.U)
      c.clock.step()

      println(s"Data: ${c.exposed_storedData.peek()}")
      println(s"Last: ${c.exposed_storedLasts.peek()}")
      println(s"Last: ${c.exposed_lasts.peek().litValue.toInt.toBinaryString}")
      c.exposed_currentIndex.expect(1.U)
      c.exposed_seriesStored.expect(1.U)
      c.exposed_transferLength.expect(1.U)
      c.exposed_transferCount.expect(1.U)
      println("N=2 test done")
    }
  }

  it should "work with fancy wrapper" in {
    val stream = PhysicalStream(new MyEl, n = 1, d = 1, c = 7)

    // test case body here
    test(new ComplexityConverterFancyWrapper(stream, 10)) { c =>
      println("N=1 test")
      // Initialize signals
      println("Initializing signals")
      c.in.last.poke(c.in.last.Lit(0 -> 0.U))
      c.in.strb.poke(1.U)
      c.in.stai.poke(0.U)
      c.in.endi.poke(0.U)
      c.in.valid.poke(false.B)
    }
  }
}
