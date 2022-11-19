module data_ram(
  input wire clka,
  input wire ena,
  input wire [3:0] wea,
  input wire [15:0] addra,
  input wire [31:0] dina,
  output reg [31:0] douta
);

sram # (
  .init(0),
  .init_coe(),
  .addr_width(16)
) inst (
  .clka(clka),
  .ena(ena),
  .wea(wea),
  .addra(addra),
  .dina(dina),
  .douta(douta)
);


endmodule
