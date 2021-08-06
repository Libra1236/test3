#! /vendor/bin/sh

target=`getprop ro.board.platform`

case "$target" in
  "kona")

    # Core control parameters for gold
    echo 2 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus
    echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres
    echo 30 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres
    echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms
    echo 3 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres

    # Core control parameters for gold+
    echo 0 > /sys/devices/system/cpu/cpu7/core_ctl/min_cpus
    echo 60 > /sys/devices/system/cpu/cpu7/core_ctl/busy_up_thres
    echo 30 > /sys/devices/system/cpu/cpu7/core_ctl/busy_down_thres
    echo 100 > /sys/devices/system/cpu/cpu7/core_ctl/offline_delay_ms
    echo 1 > /sys/devices/system/cpu/cpu7/core_ctl/task_thres

    # Controls how many more tasks should be eligible to run on gold CPUs
    # w.r.t number of gold CPUs available to trigger assist (max number of
    # tasks eligible to run on previous cluster minus number of CPUs in
    # the previous cluster).
    #
    # Setting to 1 by default which means there should be at least
    # 4 tasks eligible to run on gold cluster (tasks running on gold cores
    # plus misfit tasks on silver cores) to trigger assitance from gold+.
    echo 1 > /sys/devices/system/cpu/cpu7/core_ctl/nr_prev_assist_thresh

    # Disable Core control on silver
    echo 0 > /sys/devices/system/cpu/cpu0/core_ctl/enable

    # Setting b.L scheduler parameters
    echo 95 95 > /proc/sys/kernel/sched_upmigrate
    echo 85 85 > /proc/sys/kernel/sched_downmigrate
    echo 100 > /proc/sys/kernel/sched_group_upmigrate
    echo 85 > /proc/sys/kernel/sched_group_downmigrate
    echo 1 > /proc/sys/kernel/sched_walt_rotate_big_tasks
    echo 400000000 > /proc/sys/kernel/sched_coloc_downmigrate_ns

    # cpuset parameters
    echo 0-2 > /dev/cpuset/background/cpus
    echo 0-3 > /dev/cpuset/system-background/cpus
    echo 0-7 > /dev/cpuset/foreground/cpus
    echo 0-7 > /dev/cpuset/top-app/cpus

    # Turn off scheduler boost at the end
    echo 0 > /proc/sys/kernel/sched_boost

    # Turn on scheduler boost for top app main
    echo 1 > /proc/sys/kernel/sched_boost_top_app

    # configure governor settings for silver cluster
    echo "schedutil" > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
    echo 0 > /sys/devices/system/cpu/cpufreq/policy0/schedutil/down_rate_limit_us
    echo 0 > /sys/devices/system/cpu/cpufreq/policy0/schedutil/up_rate_limit_us
    if [ `cat /sys/devices/soc0/revision` == "2.0" ]; then
      echo 1248000 > /sys/devices/system/cpu/cpufreq/policy0/schedutil/hispeed_freq
    else
      echo 1228800 > /sys/devices/system/cpu/cpufreq/policy0/schedutil/hispeed_freq
    fi
    echo 691200 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
    echo 1 > /sys/devices/system/cpu/cpufreq/policy0/schedutil/pl

    # configure input boost settings
    echo "0:1324800" > /sys/devices/system/cpu/cpu_boost/input_boost_freq
    echo 120 > /sys/devices/system/cpu/cpu_boost/input_boost_ms
    echo "0:0 1:0 2:0 3:0 4:2342400 5:0 6:0 7:2361600" > /sys/devices/system/cpu/cpu_boost/powerkey_input_boost_freq
    echo 400 > /sys/devices/system/cpu/cpu_boost/powerkey_input_boost_ms

    # configure governor settings for gold cluster
    echo "schedutil" > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor
    echo 0 > /sys/devices/system/cpu/cpufreq/policy4/schedutil/down_rate_limit_us
    echo 0 > /sys/devices/system/cpu/cpufreq/policy4/schedutil/up_rate_limit_us
    echo 1574400 > /sys/devices/system/cpu/cpufreq/policy4/schedutil/hispeed_freq
    echo 1 > /sys/devices/system/cpu/cpufreq/policy4/schedutil/pl

    # configure governor settings for gold+ cluster
    echo "schedutil" > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor
    echo 0 > /sys/devices/system/cpu/cpufreq/policy7/schedutil/down_rate_limit_us
    echo 0 > /sys/devices/system/cpu/cpufreq/policy7/schedutil/up_rate_limit_us
    if [ `cat /sys/devices/soc0/revision` == "2.0" ]; then
        echo 1632000 > /sys/devices/system/cpu/cpufreq/policy7/schedutil/hispeed_freq
    else
      echo 1612800 > /sys/devices/system/cpu/cpufreq/policy7/schedutil/hispeed_freq
    fi
    echo 1 > /sys/devices/system/cpu/cpufreq/policy7/schedutil/pl

    echo N > /sys/module/lpm_levels/parameters/sleep_disabled
  ;;
esac

set_cpuset(){
  pgrep -f $1 | while read pid; do
    echo $pid > /dev/cpuset/$2/cgroup.procs
    echo $pid > /dev/stune/$2/cgroup.procs
  done
}

process_opt(){
  set_cpuset surfaceflinger top-app
  set_cpuset system_server top-app
  set_cpuset vendor.qti.hardware.display.composer-service top-app
  set_cpuset mediaserver background
  set_cpuset media.hwcodec background

  set_task_affinity `pgrep com.miui.home` 11111111
  set_task_affinity `pgrep com.miui.home` 11110000
}

process_opt &
