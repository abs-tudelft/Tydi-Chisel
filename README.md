# Tydi-Chisel

Tydi-Chisel allows you to transfer simple to very complex nested variable length sequences between components over a stream bus in a structured way, using familiar Chisel concepts.

## What is Tydi?
Tydi-Chisel is an implementation of Tydi interfaces and concepts in Chisel.

[Tydi](https://abs-tudelft.github.io/tydi) (Typed dataflow interface) is an open specification for streaming dataflow designs in digital circuits, allowing designers to express how composite and variable-length data structures are transferred over streams using clear, data-centric types.

[Chisel](https://www.chisel-lang.org/) (Constructing Hardware in a Scala Embedded Language) is a high-level open-source hardware description language (HDL).

With Tydi as data-streaming communication flow specification and Chisel as flexible implementation, an interface-driven design method can be followed.


## Why Tidy?

Developing hardware is notoriously difficult. Many solutions exist to lighten the load of developing implementations for hardware components. Yet, a gap exists between the easy use of complex nested (sequence) data structures in software and trying to work with them in hardware.

Tydi aims to solve this issue by proposing an open standard for streams of arbitrary variable length data-structures. A data-structure is created by nesting `Bits`, `Group`, `Union` and `Stream` elements. See the following table for the meaning of the used terms.

<style>
#tydi-terms th:first-child {
  border-right-width: 3px;
}
</style>

<table style="text-align: left" id="tydi-terms">
<thead>
  <tr style="border-bottom-width: 3px">
    <th>Term</th>
    <th>Type</th>
    <th>Software equivalent</th>
    <th>Chisel equivalent</th>
    <th>Meaning</th>
  </tr>
</thead>
<tbody>
  <tr>
    <th scope="row">Null</th>
    <td>Logical type</td>
    <td><code>Null</code></td>
    <td><code>Bits(0)</code></td>
    <td>Empty data</td>
  </tr>
  <tr>
    <th>Bit</th>
    <td>Logical type</td>
    <td>Any primary datatype</td>
    <td>Ground hardware type</td>
    <td>Primary datatype of <code>x</code> bits</td>
  </tr>
  <tr>
    <th>Group</th>
    <td>Logical type</td>
    <td><code>Struct</code>/<code>dict</code>/<code>map</code></td>
    <td><code>Bundle</code></td>
    <td>Aggregate of several logic types</td>
  </tr>
  <tr>
    <th>Union</th>
    <td>Logical type</td>
    <td><code>Union</code></td>
    <td><code>Bundle</code> with tag</td>
    <td>“pick one” of several logic types</td>
  </tr>
  <tr style="border-bottom-width: 2px">
    <th>Stream</th>
    <td>Logical type</td>
    <td>Bus to transport sequence of instance</td>
    <td>–</td>
    <td>Specify how to transport logic type</td>
  </tr>
  <tr>
    <th>Streamlet</th>
    <td>Hardware element</td>
    <td><code>Interface</code></td>
    <td><code>Trait</code> with IO defs</td>
    <td>IO specification of component</td>
  </tr>
  <tr>
    <th>Impl</th>
    <td>Hardware element</td>
    <td><code>Class</code> with functionality</td>
    <td><code>Module</code></td>
    <td>Inner structure of component</td>
  </tr>
</tbody>
</table>

By being a super-set of ready-valid communication, like Chisel's `DecoupledIO`, and the AXI-Stream standard, Tydi-interfaces stay close to existing streaming implementations.

See the work in [publications](#publications) for more details.

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

## Publications

- _C. Cromjongh, Y. Tian, Z. Al-Ars and H. P. Hofstee_  
  **Enabling Collaborative and Interface-Driven Data-Streaming Accelerator Design with Tydi-Chisel**  
  2023 IEEE Nordic Circuits and Systems Conference (NorCAS), 1 November 2023. DOI: [10.1109/NorCAS58970.2023.10305451](https://doi.org/10.1109/NorCAS58970.2023.10305451).

## License

Tydi and this library are licensed under the [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) license. See [LICENSE](./LICENSE).

## Related Tools
* [Tydi-Lang-2](https://github.com/twoentartian/tydi-lang-2)\
A tool for converting Tydi-lang code to a `json` representation of all logic types, streamlets, and implementations.
* [Tydi-Lang-2-Chisel](https://github.com/ccromjongh/Tydi-lang-2-chisel)\
Consequtively converts the `json` tydi definitions to Chisel code utilising Tydi-Chisel.
* [TyWaves](https://github.com/rameloni/tywaves-chisel-demo)\
A type based waveform viewer for Chisel and Tydi-Chisel, combining CIRCT debug output with the [Surfer](https://gitlab.com/surfer-project/surfer) waveform viewer.
* [Tydi repository](https://github.com/abs-tudelft/tydi)\
Contains Tydi standard documentation and first implementation of a compiler. 
