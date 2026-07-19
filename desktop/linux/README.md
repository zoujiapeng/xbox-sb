# PadMax Linux Server

This server creates a Linux evdev virtual gamepad through `/dev/uinput` and listens for PadMax plain UDP packets.

## Build

```bash
make
```

## Run

```bash
sudo ./padmaxd-linux
```

If you do not want to run as root, create a udev rule that grants your user access to `/dev/uinput`.

## Test

```bash
sudo evtest
# or
jstest /dev/input/js0
```
