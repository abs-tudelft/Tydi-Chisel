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

<table style="text-align: left" id="tydi-terms">
<thead>
  <tr style="border-bottom-width: 3px">
    <th style="border-right-width: 3px;">Term</th>
    <th>Type</th>
    <th>Software equivalent</th>
    <th>Chisel equivalent</th>
    <th>Meaning</th>
  </tr>
</thead>
<tbody>
  <tr>
    <th scope="row" style="border-right-width: 3px;">Null</th>
    <td>Logical type</td>
    <td><code>Null</code></td>
    <td><code>Bits(0)</code></td>
    <td>Empty data</td>
  </tr>
  <tr>
    <th scope="row" style="border-right-width: 3px;">Bit</th>
    <td>Logical type</td>
    <td>Any primary datatype</td>
    <td>Ground hardware type</td>
    <td>Primary datatype of <code>x</code> bits</td>
  </tr>
  <tr>
    <th scope="row" style="border-right-width: 3px;">Group</th>
    <td>Logical type</td>
    <td><code>Struct</code>/<code>dict</code>/<code>map</code></td>
    <td><code>Bundle</code></td>
    <td>Aggregate of several logic types</td>
  </tr>
  <tr>
    <th scope="row" style="border-right-width: 3px;">Union</th>
    <td>Logical type</td>
    <td><code>Union</code></td>
    <td><code>Bundle</code> with tag</td>
    <td>“pick one” of several logic types</td>
  </tr>
  <tr style="border-bottom-width: 2px">
    <th scope="row" style="border-right-width: 3px;">Stream</th>
    <td>Logical type</td>
    <td>Bus to transport sequence of instance</td>
    <td>–</td>
    <td>Specify how to transport logic type</td>
  </tr>
  <tr>
    <th scope="row" style="border-right-width: 3px;">Streamlet</th>
    <td>Hardware element</td>
    <td><code>Interface</code></td>
    <td><code>Trait</code> with IO defs</td>
    <td>IO specification of component</td>
  </tr>
  <tr>
    <th scope="row" style="border-right-width: 3px;">Impl</th>
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
### Using Tydi data-structures and streams
The following example provides an overview of the declaration of a nested Tydi data structure and its usage in streams.

In software, the declared data structure would look like

```json
[
  {
    "time": 123456789,
    "nested": {
      "a": 5,
      "b": true
    },
    "message": [
      "Hello", "this", "is", "a", "string."
    ]
  }
]
```

For Tydi, this gives us the following structure

![timestamped-message.svg](figures/timestamped-message.svg)

In Chisel, it can be expressed as follows

```scala
// Declaration of the data-structure 
class Character extends BitsEl(8.W)

class NestedBundle extends Group {
  val a: UInt = UInt(8.W)
  val b: Bool = Bool()
}

class TimestampedMessageBundle extends Group {
  val time: UInt               = UInt(64.W)
  val nested: NestedBundle     = new NestedBundle
  // Create a 2D sub-stream of character data with 3 data-lanes
  val message = new PhysicalStreamDetailed(Character, n = 3, d = 2, c = 7)
}

// Declaration of the module
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
See the [timestamped_message](library/src/main/scala/nl/tudelft/tydi_chisel/examples/timestamped_message/TimestampedMessage.scala) file for the full example. This file is just for showing the syntax of declaring and using Tydi-interfaces. It does not contain an implementation. For that, look at the pipeline examples in [the overview](#examples-overview) below. There you will also find advanced syntax for the creation of modules and chaining of submodules through their streams:

```scala
...

class PipelineExampleModule extends SimpleProcessorBase(new NumberGroup, new Stats) {
  out := in.processWith(new NonNegativeFilter).processWith(new Reducer)
}
```

### Testing a TydiModule
The code below shows a snippet from the [pipeline example test](testing/src/test/scala/nl/tudelft/tydi_chisel/pipeline/PipelineExampleTest.scala) code. It shows how to use the TydiStreamDriver to easily enqueue and dequeue data to sink and from source streams. Currently, this is only available for [ChiselTest](https://github.com/ucb-bar/chiseltest), and not ChiselSim.
```scala
// Import stuff

class PipelineExampleTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "PipelineExample"

  class PipelineWrap extends TydiTestWrapper(new PipelineExampleModule, new NumberGroup, new Stats)

  it should "process a sequence" in {
    // Test code
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
Look through the [test examples](testing/src/test/scala/nl/tudelft/tydi_chisel) for all functionality and syntax.

### Examples overview
- [Hello world](library/src/main/scala/nl/tudelft/tydi_chisel/examples/hello_world/HelloWorld.scala) – This example focuses on the **physical interface structure**.
<details style="margin: -1em 0 -1em 2em">
<summary>Details</summary>
It shows the signals of a Tydi interface carrying only a simple character stream.
</details>

- [Timestamped message](library/src/main/scala/nl/tudelft/tydi_chisel/examples/timestamped_message/TimestampedMessage.scala) – This example focuses on building a **data structure**.
<details style="margin: -1em 0 -1em 2em">
<summary>Details</summary>
It shows advanced/nested usage of Tydi elements (<code>Group</code>, <code>Union</code>, <code>Stream</code>). It shows how a detailed stream with nested representation is broken out to separate standard physical streams.
<br><br>
<img src="figures/timestamped-message.svg" alt="Timestamped message data structure">
</details>

- [Pipeline](library/src/main/scala/nl/tudelft/tydi_chisel/examples/pipeline/PipelineExample.scala) – This example focuses on **building modules** with Tydi interfaces.
<details style="margin: -1em 0 -1em 2em">
<summary>Details</summary>
The example shows some implementations for modules in a pipeline executing basic tasks.
<br><br>
<img src="figures/number-pipeline-simple.svg" alt="Simple pipeline diagram">
</details>

- [Advanced pipeline](library/src/main/scala/nl/tudelft/tydi_chisel/examples/pipeline/PipelineExamplePlus.scala) – This example focuses on **composing systems** with Tydi modules.
<details style="margin: -1em 0 -1em 2em">
<summary>Details</summary>
A more advanced version of the normal pipeline example that uses syntax sugar and utilities to stitch together a higher throughput version of the pipeline.
<br><br>
<img src="figures/number-pipeline-advanced.svg" alt="Advanced pipeline diagram">
</details>

- [Big system, toolchain and utilities](https://github.com/ccromjongh/Tydi-TVLSI-example) – Shows composition of a **larger system with many streams and advanced data structures**.
<details style="margin: -1em 0 -1em 2em">
<summary>Details</summary>
The system that is presented in this example uses of various Tydi-related utilities (e.g. <a href="#related-tools">related tools</a> to construct a larger system. The example focuses on system composition like the one above and does not contain functionality for most modules. It contains several sections that each execute a common function in data-processing. Macroscopically, this system processes a compressed set of records about students and their grades for different courses and computes their similarity vectors. See the project-link or <a href="#publications">publication</a>.
<br><br>
<img src="figures/VLSI-demo.svg" alt="TVLSI example system diagram">
</details>


## Getting started

### Acquire the lib
To get started with Tydi-Chisel, you can either run it in your local development environment, or use the [Tydi-tools](https://github.com/abs-tudelft/Tydi-tools) docker container.

For usage with `sbt` or `scala-cli`, the project package must first be built. Make sure you have `git`, `java`, and `sbt` installed and available in your path.

```shell
git clone https://github.com/abs-tudelft/Tydi-Chisel.git
sbt publishLocal
```

### Use the lib
After this, the library can be added to your project's dependencies in your `build.sbt` like so.

```scala
libraryDependencies += "nl.tudelft" %% "tydi-chisel" % "0.1.0"
libraryDependencies += "nl.tudelft" %% "tydi-chisel-test" % "0.1.0" % Test
```

When using `scala-cli`, use the following directive:
```scala
//> using dep "nl.tudelft::tydi-chisel::0.1.0"
```

Then, one can import the functionality, and start to write Tydi-Chisel code!

```scala
import nl.tudelft.tydi_chisel._
```

Look through the [examples](library/src/main/scala/nl/tudelft/tydi_chisel/examples) for inspiration for the functionality and syntax.

## Features

Concretely, this project contains:

- Expressing Tydi stream interfaces in Chisel
    - Including nested stream support
    - Being able to work with the detailed bundles inside your components
    - Compliant with [Tydi-standard](https://abs-tudelft.github.io/tydi/specification/physical.html) for communication with components created outside of Chisel
    - Simple stream connection
    - Stream compatibility checks
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
  **Hardware-Accelerator Design by Composition: Dataflow Component Interfaces With Tydi-Chisel**  
  2024 IEEE Transactions on Very Large Scale Integration (VLSI) Systems, 4 October 2024. DOI: [10.1109/TVLSI.2024.3461330](https://doi.org/10.1109/TVLSI.2024.3461330).
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
