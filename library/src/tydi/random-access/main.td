package random_access; //package name (rather than the file name) determines the name in memory.

// Recreating Root<Group<Bits<64>,Rev<Dim<Group<Bits<64>,Rev<Bits<8>>>>>>>
// The default of https://mbrobbel.github.io/opentyde/

const clock: clockdomain; // Default clockdomain
const throughput = 1;

const d_size = 8;
const addr_size = 64;
type addr = Bit(addr_size);

type Group request_group {
  addr: addr,
  response: Stream(Bit(d_size), d=1, t=throughput, r="reverse"),
};

type Group random_access {
  size: addr,
  requests: Stream(request_group, t=throughput, r="reverse"),
};

#streamlet documentation#
streamlet random_access {
  input: Stream(random_access, c=5, t=throughput, r="forward", x=true) in `clock,
};

#implement documentation#
impl random_access_component of random_access {
};





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