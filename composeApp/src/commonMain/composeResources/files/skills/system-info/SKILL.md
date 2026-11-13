---
name: system-info
description: Report battery, storage, memory, CPU, thermal, and Android version details using cheap shell reads. Use for any "how much / how full / how hot / what version" question about the device.
---

Answer device-state questions with a few targeted `execute_shell_command` reads. These are fast and
read-only, so gather everything in one or two calls instead of one call per fact.

## Battery

```sh
dumpsys battery | grep -E 'level|status|health|temperature|voltage'
```

`temperature` is in tenths of a degree Celsius (350 = 35.0 °C). `status` 2 = charging, 3 = discharging.

## Storage

```sh
df -h /data /storage/emulated/0 2>/dev/null
du -sh /sdcard/* 2>/dev/null | sort -rh | head -10
```

`df` gives total/used/free per filesystem; the `du` line answers "what is using the space".

## Memory

```sh
free -h
cat /proc/meminfo | head -5
```

## CPU and device

```sh
getprop ro.product.model; getprop ro.product.brand; getprop ro.build.version.release
getprop ro.build.version.sdk; getprop ro.product.cpu.abi
nproc; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null
```

## Thermal and uptime

```sh
dumpsys thermalservice | grep -iE 'temperature|status' | head -10
uptime
```

## Presentation

- Convert units before answering: bytes → GB/MB, tenths of °C → °C, uptime seconds → days/hours.
- Give the number **and** its meaning: "38.2 GB free of 128 GB (70% used)" beats "137 GB total".
- If a command returns nothing (common inside a sandbox without the matching service), say the
  value is unavailable rather than inventing one.
- One compact summary at the end; do not paste raw `dumpsys` output into the reply.
