module sram  # (
parameter init = 0,
parameter init_coe = "a",
parameter addr_width = 16
) (
  input wire clka,
  input wire ena,
  input wire [3:0] wea,
  input wire [addr_width-1:0] addra,
  input wire [31:0] dina,
  output wire [31:0] douta
);

integer i;

reg [31:0] mem [2**addr_width-1:0];
reg [addr_width-1:0] addr_pipe;

initial begin
  if(init)
    $readmemh(init_coe, mem);

end

always @(posedge clka) begin
  if(ena)
    addr_pipe <= addra;

end

assign douta = mem[addr_pipe];

always @(posedge clka) begin
  if(ena) begin
    for(i=0;i<4;i=i+1) begin:byte_assign
      if(wea[i]) begin
        mem[addra][i*8+:8] = dina[i*8+:8];
      end
    end
  end
end

endmodule