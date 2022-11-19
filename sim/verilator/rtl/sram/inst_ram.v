`ifndef INST_RAM_COE
`define INST_RAM_COE "../verify/coe/func_inst_ram.coe"
`endif

module inst_ram(
  input wire clka,
  input wire ena,
  input wire [3:0] wea,
  input wire [17:0] addra,
  input wire [31:0] dina,
  output reg [31:0] douta
);

sram # (
  .init(1),
  .init_coe(`INST_RAM_COE),
  .addr_width(18)
) inst (
  .clka(clka),
  .ena(ena),
  .wea(wea),
  .addra(addra),
  .dina(dina),
  .douta(douta)
);

endmodule
