package main; // Package name (rather than the file name) determines the name in memory.

const bit_width = 8;
const throughput = 6.0;
type char = Bit(bit_width);
const clock: clockdomain; // Default clockdomain

type stream_def = Stream(char, d=2, c=7, t=throughput, r="forward", x=true);


#hello_world_in streamlet documentation#
streamlet hello_world_in {
  input: stream_def in `clock,
};

#hello_world_impl documentation.
Interesting stuff.#
impl hello_world_impl of hello_world_in {
  // input => output @NoStrictType@,
  // input2 => output2,
};



//// STD LIB ////

//void component, always acknowledge the handshake
streamlet void_s<type_in: type> {
  input: type_in in,
};

external impl void_i<type_in: type> of void_s<type type_in> {

};

//data duplicator
streamlet duplicator_s<data_type: type, output_channel: int> {
  input: data_type in,
  output: data_type [output_channel] out,
};

external impl duplicator_i<data_type: type, output_channel: int> of duplicator_s<type data_type, output_channel> {

};