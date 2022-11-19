proc resim { args } {
  set cur_time [current_time]

  set number_end [string wordend $cur_time 0]
  set number [string range $cur_time 0 $number_end]
  set unit_end [string wordend $cur_time [expr $number_end + 1]]
  set unit [string range $cur_time [expr $number_end + 1] [expr $unit_end + 1]]

  relaunch_sim
  run [string trim $number] $unit
}
