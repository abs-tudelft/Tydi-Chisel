# Tydi-Chisel
[Tydi](https://abs-tudelft.github.io/tydi) (Typed dataflow interface) is an open specification for streaming dataflow designs in digital circuits, allowing designers to express how composite and variable-length data structures are transferred over streams using clear, data-centric types.

[Chisel](https://www.chisel-lang.org/) (Constructing Hardware in a Scala Embedded Language) is a high-level open-source hardware description language (HDL).

With Tydi as data-streaming communication flow specification and Chisel as flexible implementation, an interface-driven design method can be followed.

Tydi-Chisel is an implementation of Tydi interfaces and concepts in Chisel.

Concretely, it contains:

- Expressing Tydi stream interfaces in Chisel
    - Including nested stream support
    - Being able to work with the detailed bundles inside your components
    - Compliant with Tydi-standard for communication with components created outside of Chisel
    - Helper functions for common signal use-cases
- A [Tydi-lang-2-Chisel](https://github.com/ccromjongh/tydi-lang-2-chisel) transpiler  
  to convert Tydi interfaces described in Tydi-lang-2 to Chisel code utilizing the Tydi-Chisel library.
    - A reverse-transpiler to share your Tydi-Chisel code as Tydi-lang-2 code
- A stream-processing component chaining syntax
- Testing utilities
    - `chisel-test` driver for Tydi stream interfaces.
- Helper components
    - A _complexity converter_ that can convert any incoming stream to the lowest source complexity  
     \* Not all complexity conversion steps are included yet
    - A _multi-processor_ or _interleaving_ component that splits a multi-lane stream into multiple single lane streams for easy processing, merging them again after.

## Related Tools
* [tydi-lang-2](https://github.com/twoentartian/tydi-lang-2)  
A tool for converting Tydi-lang code to a `json` representation of all logic types, streamlets, and implementations
* [tydi-lang-2-chisel](https://github.com/twoentartian/tydi-lang-2)  
Consequtively converts the `json` tydi definitions to Chisel code utilising Tydi-Chisel
* [Tydi repository](https://github.com/abs-tudelft/tydi)  
Contains Tydi standard documentation and first implementation of a compiler. 
