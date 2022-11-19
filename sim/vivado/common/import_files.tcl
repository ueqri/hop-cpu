set VERILOG_FILES [concat \
  $script_dir/../genrtl/mycpu_top.v \
  ]

# add VERILOG_FILES file
foreach VERILOG_FILE $VERILOG_FILES {
	set file_path [file normalize ${VERILOG_FILE}]
	if {!($file_path in [get_files])} {
	  add_files $file_path
	}
}
