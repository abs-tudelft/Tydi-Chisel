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


class StreamConverterTest extends AnyFlatSpec with ChiselScalatestTester {
  class MyEl extends Group {
    val a: UInt = UInt(8.W)
    val b: UInt = UInt(4.W)
  }

  def b(num: String): UInt = {
    Integer.parseInt(num, 2).U
  }

  def bRev(num: String): UInt = {
    Integer.parseInt(num.reverse, 2).U
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
    val in: PhysicalStreamDetailed[T, Null] = IO(Flipped(new PhysicalStreamDetailed(el, in_ref.n, in_ref.d, c=8, r = true)))

    out := mod.out
    mod.in := in

    val exposed_indexMask: UInt = expose(in.indexMask)
    val exposed_laneValidity: UInt = expose(in.laneValidity)
    val exposed_incrementIndexAt: UInt = expose(mod.incrementIndexAt)
    val exposed_lastSeqs: UInt = expose(mod.lastSeqProcessor.outCheck)
    val exposed_reducedLasts: Vec[UInt] = expose(mod.lastSeqProcessor.reducedLasts)
    val exposed_prevReducedLast: UInt = expose(mod.prevReducedLast)
    val exposed_currentWriteIndex: UInt = expose(mod.currentWriteIndex)
    val exposed_seriesStored: UInt = expose(mod.seriesStored)
    val exposed_outItemsReadyCount: UInt = expose(mod.outItemsReadyCount)
    val exposed_transferOutItemCount: UInt = expose(mod.transferOutItemCount)
    val exposed_lanesIn: Vec[UInt] = expose(mod.lanesIn)
    val exposed_lastLanesIn: Vec[UInt] = expose(mod.lastsIn)
    val exposed_storedData: Vec[UInt] = expose(mod.dataReg)
    val exposed_storedLasts: Vec[UInt] = expose(mod.lastReg)
    val exposed_storedEmpties: Vec[Bool] = expose(mod.emptyReg)
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
      c.exposed_transferOutItemCount.expect(1.U)
      println("Step clock")
      c.clock.step()

      println(s"Data: ${printVec(c.exposed_storedData.peek())}")
      println(s"Last: ${printVecBinary(c.exposed_storedLasts.peek())}")
      println(s"Last: ${binaryFromUint(c.exposed_lasts.peek())}")
      c.exposed_seriesStored.expect(1.U) // Still outputting first series
      c.out.valid.expect(1.U) // ... means valid output
      c.exposed_currentWriteIndex.expect(1.U)
      c.exposed_outItemsReadyCount.expect(1.U)
      c.exposed_transferOutItemCount.expect(1.U)
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
      val litValIn1 = c.in.elLit(_.a -> 136.U, _.b -> 9.U)
      println(litValIn1)
      println(litValIn1.litValue.toInt.toBinaryString)

      // Send some data in
      c.in.enqueueElNow(_.a -> 136.U, _.b -> 9.U)
      c.clock.step(3)  // Check if the circuit holds its state
      c.in.enqueueElNow(_.a -> 65.U, _.b -> 4.U)
      c.exposed_currentWriteIndex.expect(2.U)
      c.exposed_seriesStored.expect(0.U)
      c.out.expectInvalid()
      c.in.last(0).poke(1.U)
      c.in.enqueueElNow(_.a -> 98.U, _.b -> 7.U)
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

  private val char = BitsEl(8.W)

  implicit class CharExtensions1(c: Char) {
    def asEl: BitsEl = char.Lit(_.value -> c.U)
  }

  implicit class CharExtensions2(c: BitsEl) {
    def asChar: Char = c.litValue.toChar
  }

  implicit class CharExtensions3(c: UInt) {
    def asChar: Char = c.litValue.toChar
  }

  implicit class StringExtensions(s: Seq[UInt]) {
    def asString: String = s.map(_.asChar).mkString
  }

  def printInputState(c: ManualComplexityConverterFancyWrapper[BitsEl]): Unit = {
    println(s"reducedLasts: ${binaryFromUint(c.exposed_prevReducedLast.peek())} -> ${printVecBinary(c.exposed_reducedLasts.peek())}")
    println(s"lastSeqs: ${binaryFromUint(c.exposed_lastSeqs.peek())}")
  }

  def printOutputState(c: ManualComplexityConverterFancyWrapper[BitsEl]): Unit = {
    print(c.out.printState())
    println(s"Data: ${c.out.data.peek().map(_.asChar)}")
    println(s"Items ready: ${c.exposed_outItemsReadyCount.peekInt()}, transfer: ${c.exposed_transferOutItemCount.peekInt()}")
    println(s"Last: ${printVecBinary(c.out.last.peek())}")
    println(s"Stai: ${c.out.stai.peek().litValue}, Endi: ${c.out.endi.peek().litValue}")
  }

  it should "process 'she is a dolphin'" in {
    val stream = PhysicalStream(char, n = 4, d = 2, c = 8)

    def vecLitFromString(s: String): Vec[BitsEl] = {
      val mapping = s.map(c => char.Lit(_.value -> c.U)).zipWithIndex.map(v => (v._2, v._1))
      Vec(4, new BitsEl(8.W)).Lit(mapping: _*)
    }

    // test case body here
    test(new ManualComplexityConverterFancyWrapper(char, stream, 20)) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)
      c.in.endi.poke(stream.n-1)
      c.in.strb.poke(0.U)
      println("She is a dolphin test")
      println("Initializing signals")
      c.exposed_currentWriteIndex.expect(0.U)
      c.exposed_seriesStored.expect(0.U)

      val t1 = vecLitFromString("shei")
      val t3 = c.in.dataLit(0 -> 's'.asEl, 2 -> 'a'.asEl)
      val t4 = c.in.dataLit(0 -> 'd'.asEl, 2 -> 'o'.asEl, 3 -> 'l'.asEl)
      val t5 = c.in.dataLit(0 -> 'p'.asEl, 1 -> 'h'.asEl)
      val t6 = c.in.dataLit(2 -> 'i'.asEl, 3 -> 'n'.asEl)

      val lastType = Vec(stream.n, UInt(stream.d.W))

      parallel(
        {
          // Send some data in
          // shei
          timescope({
            c.in.valid.poke(true)
            c.in.data.pokePartial(t1)
            c.in.strb.poke(bRev("1111"))
            c.in.last.poke(Vec.Lit("b00".U(2.W), "b00".U, "b01".U, "b00".U))
            println("\n-- Transfer in 1")
            printInputState(c)
            c.clock.step(1)
            println(s"All data: ${c.exposed_storedData.peek().asString}")
          })

          timescope({
            c.clock.step(1)
          })

          // s_a_
          timescope({
            c.in.valid.poke(true)
            c.in.data.pokePartial(t3)
            c.in.strb.poke(bRev("1010"))
            c.in.last.poke(Vec.Lit("b01".U(2.W), "b00".U, "b01".U, "b00".U))
            println("\n-- Transfer in 3")
            printInputState(c)
            c.clock.step(1)
            println(s"All data: ${c.exposed_storedData.peek().asString}")
          })

          // d_ol
          timescope({
            c.in.valid.poke(true)
            c.in.data.pokePartial(t4)
            c.in.strb.poke(bRev("1011"))
            c.in.last.poke(Vec.Lit("b00".U(2.W), "b00".U, "b00".U, "b00".U))
            println("\n-- Transfer in 4")
            printInputState(c)
            c.clock.step(1)
            println(s"All data: ${c.exposed_storedData.peek().asString}")
          })

          // ph__
          timescope({
            c.in.valid.poke(true)
            c.in.data.pokePartial(t5)
            c.in.strb.poke(bRev("1100"))
            c.in.last.poke(Vec.Lit("b00".U(2.W), "b00".U, "b00".U, "b00".U))
            println("\n-- Transfer in 5")
            printInputState(c)
            c.clock.step(1)
            println(s"All data: ${c.exposed_storedData.peek().asString}")
          })

          // __in
          timescope({
            c.in.valid.poke(true)
            c.in.data.pokePartial(t6)
            c.in.strb.poke(bRev("0011"))
            println("\n-- Transfer in 6")
            printInputState(c)
            c.clock.step(1)
            println(s"All data: ${c.exposed_storedData.peek().asString}")
          })

          // close off
          timescope({
            c.in.valid.poke(true)
            c.in.last.poke(Vec.Lit("b11".U(2.W), "b00".U, "b00".U, "b00".U))
            println("\n-- Transfer in 7")
            printInputState(c)
            c.clock.step(1)
          })

          println(s"All data: ${c.exposed_storedData.peek().asString}")
          println(s"All lasts: ${printVecBinary(c.exposed_storedLasts.peek())}")
        },
        {
          c.out.waitForValid()
          c.out.ready.poke(true)
          println("\n\n--  Output data  --")
          println("\n-- Transfer out 1")
          printOutputState(c)
          c.out.data.expectPartial(vecLitFromString("she"))
          c.out.last.last.expect("b01".U)
          c.out.endi.expect(2.U)
          c.clock.step(1)

          println("\n-- Transfer out 2")
          c.out.data.expectPartial(vecLitFromString("is"))
          c.out.last.last.expect("b01".U)
          c.out.endi.expect(1.U)
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 3")
          c.out.data.expectPartial(vecLitFromString("a"))
          c.out.last.last.expect("b01".U)
          c.out.endi.expect(0.U)
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 4")
          c.out.data.expectPartial(vecLitFromString("dolp"))
          c.out.last.last.expect("b00".U)
          c.out.endi.expect(3.U)
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 5")
          c.out.data.expectPartial(vecLitFromString("hin"))
          c.out.last.last.expect("b11".U)
          c.out.endi.expect(2.U)
          printOutputState(c)

          c.clock.step(1)
          // Transfer should be done now.
          println("\n-- Transfer out 6")
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 7")
          printOutputState(c)
        }
      )
    }
  }

  it should "process 'hello world'" in {
    val stream = PhysicalStream(char, n = 6, d = 2, c = 8)

    def vecLitFromString(s: String): Vec[BitsEl] = {
      val mapping = s.map(c => char.Lit(_.value -> c.U)).zipWithIndex.map(v => (v._2, v._1))
      Vec(6, new BitsEl(8.W)).Lit(mapping: _*)
    }


    // test case body here
    test(new ManualComplexityConverterFancyWrapper(char, stream, 20)) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)
      c.in.endi.poke(stream.n-1)
      c.in.strb.poke(0.U)
      println("Hello World test")
      println("Initializing signals")
      c.exposed_currentWriteIndex.expect(0.U)
      c.exposed_seriesStored.expect(0.U)

      // Sending ["Hello", "World"], ["Tydi", "is", "nice"], [""], []
      val t1 = vecLitFromString("HelloW")
      val t2 = vecLitFromString("orldTy")
      val t3 = vecLitFromString("diisni")
      val t4 = vecLitFromString("ce")

      val lastType = Vec(stream.n, UInt(stream.d.W))

      parallel(
        {
          // Send some data in
          // HelloW
          c.in.enqueueNow(t1,
            last = Some(Vec.Lit("b00".U(2.W), "b00".U, "b00".U, "b00".U, "b01".U, "b00".U)),
            strb = Some(bRev("111111")))
          println("\n-- Transfer in 1")
          println(s"All data: ${c.exposed_storedData.peek().asString}")

          // orldTy
          c.in.enqueueNow(t2,
            last = Some(Vec.Lit("b00".U(2.W), "b00".U, "b00".U, "b11".U, "b00".U, "b00".U)),
            strb = Some(bRev("111111")))
          println("\n-- Transfer in 2")
          println(s"All data: ${c.exposed_storedData.peek().asString}")

          // diisni
          c.in.enqueueNow(t3,
            last = Some(Vec.Lit("b00".U(2.W), "b01".U, "b00".U, "b01".U, "b00".U, "b00".U)),
            strb = Some(bRev("111111")))
          println("\n-- Transfer in 3")
          println(s"All data: ${c.exposed_storedData.peek().asString}")

          // ce____
          c.in.enqueueNow(t4,
            last = Some(Vec.Lit("b00".U(2.W), "b00".U, "b01".U, "b10".U, "b11".U, "b10".U)),
            strb = Some(bRev("110000")))
          println("\n-- Transfer in 4")
          println(s"All data: ${c.exposed_storedData.peek().asString}")

          println(s"All data: ${c.exposed_storedData.peek().asString}")
          println(s"All lasts: ${printVecBinary(c.exposed_storedLasts.peek())}")
        },
        {
          c.out.waitForValid()
          c.out.ready.poke(true)
          println("\n-- Transfer out 1")
          printOutputState(c)
          c.out.data.expectPartial(vecLitFromString("Hello"))
          c.out.last.last.expect("b01".U)
          c.out.endi.expect(4.U)
          c.clock.step(1)

          println("\n-- Transfer out 2")
          c.out.data.expectPartial(vecLitFromString("World"))
          c.out.last.last.expect("b11".U)
          c.out.endi.expect(4.U)
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 3")
          c.out.data.expectPartial(vecLitFromString("Tydi"))
          c.out.last.last.expect("b01".U)
          c.out.endi.expect(3.U)
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 4")
          c.out.data.expectPartial(vecLitFromString("is"))
          c.out.last.last.expect("b01".U)
          c.out.endi.expect(1.U)
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 5")
          c.out.data.expectPartial(vecLitFromString("nice"))
          c.out.last.last.expect("b11".U)
          c.out.endi.expect(3.U)
          println(s"All data: ${c.exposed_storedData.peek().asString}")
          println(s"All lasts: ${printVecBinary(c.exposed_storedLasts.peek())}")
          printOutputState(c)

          c.clock.step(1)
          // Transfer should be done now.
          println("\n-- Transfer out 6")
          c.out.last.last.expect("b11".U)
          c.out.strb.expect(0.U)
          c.out.endi.expect(0.U)
          printOutputState(c)

          c.clock.step(1)
          println("\n-- Transfer out 7")
          c.out.last.last.expect("b10".U)
          c.out.strb.expect(0.U)
          c.out.endi.expect(0.U)
          printOutputState(c)
        }
      )
    }
  }
}
