---
name: network-control
description: Inspect connectivity, IP addresses, Wi-Fi details, DNS, and ping/traceroute from the phone. Use for "what's my IP", "is it online", "why is the network slow" questions.
---

Read-only network diagnostics through `execute_shell_command`. Everything here is safe to run
without confirmation.

## Is it online, and how

```sh
dumpsys connectivity | grep -E 'NetworkAgentInfo|Active default network' | head -10
ping -c 3 8.8.8.8
```

`ping` proves reachability; if it fails, try `ping -c 1 <gateway>` to separate "no LAN" from
"no internet".

## Addresses

```sh
ip -4 addr show | grep -E 'inet |^[0-9]+:'
ip route | head -5                                 # default gateway
getprop | grep -iE 'dns' | head -5
```

Local IP comes from `ip addr`; the public IP needs an outbound request, which the `fetch_url` tool
handles better than a shell curl inside the sandbox.

## Wi-Fi

```sh
dumpsys wifi | grep -E 'mWifiInfo|SSID|RSSI|LinkSpeed' | head -10
cmd wifi status
```

RSSI above -50 dBm is excellent, -60 to -70 is fair, below -80 is poor. Mention that scale when the
user asks whether the signal is good.

## Rules

- Never print the full `dumpsys` output; grep for the fields you need.
- MAC addresses and SSIDs are identifying. Include them only when the user asked for them.
- If a diagnostic needs the sandbox and the sandbox is not installed, say the check is unavailable
  instead of guessing a result.
