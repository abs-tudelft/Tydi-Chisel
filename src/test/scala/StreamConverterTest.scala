import chisel3._
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
  }

  behavior of "ComplexityConverter"
  // test class body here

  it should "initialize" in {
    val stream = PhysicalStream(new MyEl, n = 1, d = 1, c = 7)

    // test case body here
    test(new ComplexityConverterWrapper(stream, 10)) { c =>
      // Initialize signals
      c.in.last.poke(0.U)
      c.in.strb.poke(1.U)
      c.in.stai.poke(0.U)
      c.in.endi.poke(0.U)
      c.in.valid.poke(false.B)
      c.in.data.poke(555.U)
      c.exposed_currentIndex.expect(0.U) // No items yet
      c.clock.step()

      c.exposed_currentIndex.expect(0.U) // No items yet

      // Set some data
      c.in.valid.poke(true.B)
      c.in.data.poke(555.U)
      c.exposed_currentIndex.expect(0.U) // No items yet
      c.clock.step()

      c.exposed_currentIndex.expect(1.U)
      c.exposed_transferLength.expect(1.U)
      c.exposed_seriesStored.expect(0.U)
      c.in.data.poke(0xABC.U)
      c.in.strb.poke(1.U)
      c.in.last.poke(1.U)
      c.out.ready.expect(0.U) // No full series stored yet
      c.clock.step()

      c.in.last.poke(0.U)
      c.in.valid.poke(false.B)
      c.out.ready.expect(1.U) // No full series stored yet
      c.exposed_currentIndex.expect(2.U)
      c.exposed_seriesStored.expect(1.U)
    }
  }

  it should "do something" in {
    val stream = PhysicalStream(new MyEl, n=2, d=1, c=7)

    // test case body here
    test(new ComplexityConverterWrapper(stream, 10)) { c =>
      // test body here
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
      c.in.last.poke(b("0011"))
      c.clock.step()

      c.in.last.poke(0.U)
      c.in.valid.poke(false.B)
      c.exposed_currentIndex.expect(3.U)
      c.exposed_seriesStored.expect(1.U)
    }
  }
}
