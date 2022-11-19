set script_location [file normalize [info script]]
set script_dir [file dirname $script_location]

source $script_dir/common.tcl

launch_runs synth_1
wait_on_run synth_1
