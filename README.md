# Tydi-Chisel
[Tydi](https://abs-tudelft.github.io/tydi) (Typed dataflow interface) is an open specification for streaming dataflow designs in digital circuits, allowing designers to express how composite and variable-length data structures are transferred over streams using clear, data-centric types.

[Chisel](https://www.chisel-lang.org/) (Constructing Hardware in a Scala Embedded Language) is a high-level open-source hardware description language (HDL).

With Tydi as data-streaming communication flow specification and Chisel as flexible implementation, an interface-driven design method can be followed.

Tydi-Chisel is an implementation of Tydi interfaces and concepts in Chisel.

Concretely, it contains:

## Example

Using Tydi streams in a components
```scala
class Character extends BitsEl(8.W)

class NestedBundle extends Group {
  val a: UInt = UInt(8.W)
  val b: Bool = Bool()
}

class TimestampedMessageBundle extends Group {
  val time: UInt               = UInt(64.W)
  val nested: NestedBundle     = new NestedBundle
  val message = new PhysicalStreamDetailed(Character, n = 3, d = 2, c = 7)
}

class TimestampedMessageModule extends TydiModule {
  // Create Tydi logical stream object
  val stream = PhysicalStreamDetailed(new TimestampedMessageBundle, 1, c = 7)

  // Create and connect physical streams as IO
  // following the Tydi standard with concatenated data bitvector
  val tydi_port_top   = stream.toPhysical
  val tydi_port_child = stream.el.message.toPhysical

  // → Assign values to logical stream group elements directly
  stream.el.time := System.currentTimeMillis().U
  stream.el.nested.a := 5.U
  stream.el.nested.b := true.B
  stream.el.message.data(0).value := 'H'.U
  stream.el.message.data(1).value := 'e'.U
  stream.el.message.data(2).value := 'l'.U
  ...
}
```

Testing a TydiModule
```scala
import chisel3._
import chiseltest._
import nl.tudelft.tydi_chisel.{TydiProcessorTestWrapper, TydiTestWrapper}
import nl.tudelft.tydi_chisel_test.Conversions._
import org.scalatest.flatspec.AnyFlatSpec

class PipelineExampleTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "PipelineExample"

  class PipelineWrap extends TydiTestWrapper(new PipelineExampleModule, new NumberGroup, new Stats)

  it should "process a sequence" in {
    test(new PipelineWrap) { c =>
      // Initialize signals
      c.in.initSource()
      c.out.initSink()

      // Generate list of random numbers
      val nums = randomSeq(n = 50)
      val stats = processSeq(nums) // Software impl.

      // Test component
      parallel({
        for ((elem, i) <- nums.zipWithIndex) {
          c.in.enqueueElNow(_.time -> i.U, _.value -> elem.S)
        }
        c.in.enqueueEmptyNow(last = Some(c.in.lastLit(0 -> 1.U)))
      }, {
        c.out.waitForValid()
        // Utility for comprehensively printing stream state
        println(c.out.printState(statsRenderer))
        c.out.expectDequeue(_.min -> stats.min.U, _.max -> stats.max.U, _.sum -> stats.sum.U, _.average -> stats.average.U)
      })
    }
  }
}
```

## Features

Concretely, this project contains:

- Expressing Tydi stream interfaces in Chisel
    - Including nested stream support
    - Being able to work with the detailed bundles inside your components
    - Compliant with [Tydi-standard](https://abs-tudelft.github.io/tydi/specification/physical.html) for communication with components created outside of Chisel
    - Simple stream connection
    - Helper functions for common signal use-cases
- A stream-processing component chaining syntax
- Testing utilities
    - `chisel-test` driver for Tydi stream interfaces.
- Helper components
    - A _stream complexity converter_ that can convert any incoming stream to the lowest source complexity
    - A _multi-processor_ or _interleaving_ component that splits a multi-lane stream into multiple single lane streams for easy processing, merging them again after.
- A [Tydi-lang-2-Chisel](https://github.com/ccromjongh/tydi-lang-2-chisel) transpiler\
  to convert Tydi interfaces described in Tydi-lang-2 to Chisel code utilizing the Tydi-Chisel library.
  - A reverse-transpiler to share your Tydi-Chisel code as Tydi-lang-2 code

### Future work

- Projects
  - Provide more worked out real-life examples
  - Create interoperability with [Fletcher](https://github.com/abs-tudelft/fletcher)
  - Investigate adoption of other hardware description languages
- Library
  - Stream compatibility checks
  - Better Union support
  - Interoperability with other streaming standards
  - Improved error handling and design rule checks
  - Additional helper components
- Testing and debugging
  - Change testing front-end to the new ChiselSim framework.\
    This can currently not happen yet as ChiselSim still lacks support for `parallel` calls, which makes it hard to do asynchronous component testing.
  - Improved tooling for enqueueing and dequeueing of test data
  - Stream protocol compliance verification tooling
  - Enhanced support for [TyWaves](https://github.com/rameloni/tywaves-chisel-demo)
    - Provide more time-domain information

## Related Tools
* [Tydi-Lang-2](https://github.com/twoentartian/tydi-lang-2)\
A tool for converting Tydi-lang code to a `json` representation of all logic types, streamlets, and implementations.
* [Tydi-Lang-2-Chisel](https://github.com/ccromjongh/Tydi-lang-2-chisel)\
Consequtively converts the `json` tydi definitions to Chisel code utilising Tydi-Chisel.
* [TyWaves](https://github.com/rameloni/tywaves-chisel-demo)\
A type based waveform viewer for Chisel and Tydi-Chisel, combining CIRCT debug output with the [Surfer](https://gitlab.com/surfer-project/surfer) waveform viewer.
* [Tydi repository](https://github.com/abs-tudelft/tydi)\
Contains Tydi standard documentation and first implementation of a compiler. 
