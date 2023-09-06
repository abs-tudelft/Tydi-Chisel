import chisel3._
import chiseltest._
import chiseltest.experimental.expose
import org.scalatest.flatspec.AnyFlatSpec
import tydi_lib._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.AddVecLiteralConstructor
import tydi_lib.testing.Conversions._
import tydi_lib.testing.printUtils.{binaryFromUint, printVec, printVecBinary}
import tydi_lib.utils.ComplexityConverter


class StreamConverterTest extends AnyFlatSpec with ChiselScalatestTester {
  class MyEl extends Group {
    val a: UInt = UInt(8.W)
    val b: UInt = UInt(4.W)
  }

  def b(num: String): UInt = {
    Integer.parseInt(num, 2).U
  }

  class ComplexityConverterWrapper(template: PhysicalStream, memSize: Int) extends ComplexityConverter(template, memSize) {
    val exposed_indexMask: UInt = expose(in.indexMask)
    val exposed_laneValidity: UInt = expose(in.laneValidity)
    val exposed_currentWriteIndex: UInt = expose(currentWriteIndex)
    val exposed_seriesStored: UInt = expose(seriesStored)
    val exposed_outItemsReadyCount: UInt = expose(outItemsReadyCount)
    val exposed_transferOutItemCount: UInt = expose(transferOutItemCount)
    val exposed_lanesIn: Vec[UInt] = expose(lanesIn)
    val exposed_lastLanesIn: Vec[UInt] = expose(lastsIn)
    val exposed_storedData: Vec[UInt] = expose(storedData)
    val exposed_storedLasts: Vec[UInt] = expose(storedLasts)
    val exposed_writeIndexes: Vec[UInt] = expose(writeIndexes)
    val exposed_lasts: UInt = expose(leastSignificantLastSignal)
  }

  class ComplexityConverterFancyWrapper(template: PhysicalStream, memSize: Int) extends TydiTestWrapper(new ComplexityConverter(template, memSize), new MyEl, new MyEl)

  class ManualComplexityConverterFancyWrapper[T <: TydiEl](el: T, template: PhysicalStream, memSize: Int) extends TydiModule {
    val mod: ComplexityConverter = Module(new ComplexityConverter(template, memSize))
    private val out_ref = mod.out
    private val in_ref = mod.in
    val out: PhysicalStreamDetailed[T, Null] = IO(new PhysicalStreamDetailed(el, out_ref.n, out_ref.d, out_ref.c, r = false))
    val in: PhysicalStreamDetailed[T, Null] = IO(Flipped(new PhysicalStreamDetailed(el, in_ref.n, in_ref.d, c=1, r = true)))

    out := mod.out
    mod.in := in

    val exposed_currentWriteIndex: UInt = expose(mod.currentWriteIndex)
    val exposed_seriesStored: UInt = expose(mod.seriesStored)
    val exposed_outItemsReadyCount: UInt = expose(mod.outItemsReadyCount)
    val exposed_transferOutItemCount: UInt = expose(mod.transferOutItemCount)
    val exposed_lanesIn: Vec[UInt] = expose(mod.lanesIn)
    val exposed_lastLanesIn: Vec[UInt] = expose(mod.lastsIn)
    val exposed_storedData: Vec[UInt] = expose(mod.storedData)
    val exposed_storedLasts: Vec[UInt] = expose(mod.storedLasts)
    val exposed_writeIndexes: Vec[UInt] = expose(mod.writeIndexes)
    val exposed_lasts: UInt = expose(mod.leastSignificantLastSignal)
    val outDataRaw: UInt = expose(mod.out.data)
    val inDataRaw: UInt = expose(mod.in.data)
  }

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
      c.exposed_currentWriteIndex.expect(0.U) // No items yet
      println(s"Indexes: ${printVec(c.exposed_writeIndexes.peek())}")
      println("Step clock")
      c.clock.step()

      c.exposed_currentWriteIndex.expect(0.U) // No items yet

      println("Making data valid")
      // Set some data
      c.in.valid.poke(true.B)
      c.in.data.poke(555.U)
      c.exposed_currentWriteIndex.expect(0.U) // No items yet
      println("Step clock")
      c.clock.step()

      println(s"Data: ${c.exposed_storedData(0).peek()}")
      println(s"Last: ${c.exposed_storedLasts(0).peek()}")
      println(s"Last: ${binaryFromUint(c.exposed_lasts.peek())}")
      c.exposed_currentWriteIndex.expect(1.U)
      c.exposed_transferOutItemCount.expect(0.U) // Not transferring yet because output is not ready
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
      c.exposed_currentWriteIndex.expect(2.U)
      println(s"Last: ${printVecBinary(c.exposed_storedLasts.peek())}")
      println(s"Last: ${binaryFromUint(c.exposed_lasts.peek())}")
      c.exposed_outItemsReadyCount.expect(1.U)
      println("Step clock")
      c.clock.step()

      println(s"Data: ${printVec(c.exposed_storedData.peek())}")
      println(s"Last: ${printVecBinary(c.exposed_storedLasts.peek())}")
      println(s"Last: ${binaryFromUint(c.exposed_lasts.peek())}")
      c.exposed_seriesStored.expect(1.U) // Still outputting first series
      c.out.valid.expect(1.U) // ... means valid output
      c.exposed_currentWriteIndex.expect(1.U)
      c.exposed_outItemsReadyCount.expect(1.U)
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
      c.exposed_currentWriteIndex.expect(0.U) // No items yet
      println(s"Data in strb: ${binaryFromUint(c.in.strb.peek())}")
      println(s"Data in stai: ${binaryFromUint(c.in.stai.peek())}, endi: ${binaryFromUint(c.in.endi.peek())}")
      println(s"Data in mask: ${binaryFromUint(c.exposed_indexMask.peek())}")
      println(s"Data in validity: ${binaryFromUint(c.exposed_laneValidity.peek())}")
      c.clock.step()

      println(s"Data: ${printVec(c.exposed_storedData.peek())}")
      println(s"Last: ${printVecBinary(c.exposed_storedLasts.peek())}")
      c.exposed_currentWriteIndex.expect(1.U)
      c.in.data.poke(0xABCDEF.U)
      c.in.strb.poke(b("11"))
      c.in.last.poke(b("10"))
      c.clock.step()

      println(s"Data: ${printVec(c.exposed_storedData.peek())}")
      println(s"Last: ${printVecBinary(c.exposed_storedLasts.peek())}")
      println(s"Last: ${c.exposed_lasts.peek().litValue.toInt.toBinaryString}")
      c.in.last.poke(0.U)
      c.in.valid.poke(false.B)
      c.exposed_currentWriteIndex.expect(3.U)
      c.exposed_seriesStored.expect(1.U)
      c.out.ready.poke(true.B)
      c.exposed_outItemsReadyCount.expect(2.U)
      c.exposed_transferOutItemCount.expect(2.U)
      c.clock.step()

      println(s"Data: ${printVec(c.exposed_storedData.peek())}")
      println(s"Last: ${printVecBinary(c.exposed_storedLasts.peek())}")
      println(s"Last: ${binaryFromUint(c.exposed_lasts.peek()).reverse}")
      c.exposed_currentWriteIndex.expect(1.U)
      c.exposed_seriesStored.expect(1.U)
      c.exposed_outItemsReadyCount.expect(1.U)
      c.exposed_transferOutItemCount.expect(1.U)
      println("N=2 test done")
    }
  }

  it should "work with fancy wrapper" in {
    val stream = PhysicalStream(new MyEl, n = 1, d = 1, c = 7)

    // test case body here
    test(new ManualComplexityConverterFancyWrapper(new MyEl, stream, 10)) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)
      println("N=1 test with fancy wrapper")
      println("Initializing signals")
//      c.in.last.poke(c.in.last.Lit(0 -> 0.U))
      c.exposed_currentWriteIndex.expect(0.U)
      c.exposed_seriesStored.expect(0.U)

      println("Data in:")
      val litValIn1 = c.in.dataLit(_.a -> 136.U, _.b -> 9.U)
      println(litValIn1)
      println(litValIn1.litValue.toInt.toBinaryString)

      // Send some data in
      c.in.enqueueNow(_.a -> 136.U, _.b -> 9.U)
      c.clock.step(3)  // Check if the circuit holds its state
      c.in.enqueueNow(_.a -> 65.U, _.b -> 4.U)
      c.exposed_currentWriteIndex.expect(2.U)
      c.exposed_seriesStored.expect(0.U)
      c.out.expectInvalid()
      c.in.last(0).poke(1.U)
      c.in.enqueueNow(_.a -> 98.U, _.b -> 7.U)
      c.in.last(0).poke(0.U)
      c.exposed_currentWriteIndex.expect(3.U)
      c.exposed_seriesStored.expect(1.U)

      // Get data out now that a series is available
      println("Data out:")
      println(c.out.el.peek())
      println(c.out.el.peek().litValue.toInt.toBinaryString)
      println("Data out raw:")
      println(c.outDataRaw.peek().litValue.toInt.toBinaryString)
      c.out.expectDequeueNow(_.a -> 136.U, _.b -> 9.U)
      c.exposed_currentWriteIndex.expect(2.U)
      c.exposed_seriesStored.expect(1.U)
      c.clock.step(3) // Check if the circuit holds its state
      c.out.expectDequeueNow(_.a -> 65.U, _.b -> 4.U)
      c.exposed_currentWriteIndex.expect(1.U)
      c.exposed_seriesStored.expect(1.U)
      c.out.expectDequeueNow(_.a -> 98.U, _.b -> 7.U)
      // We should be out of data now
      c.exposed_currentWriteIndex.expect(0.U)
      c.exposed_seriesStored.expect(0.U)
      c.out.expectInvalid()
    }
  }
}
