set script_location [file normalize [info script]]
set script_dir [file dirname $script_location]

source $script_dir/common.tcl

launch_simulation -mode "behavioral"

# skip reset time
run all
