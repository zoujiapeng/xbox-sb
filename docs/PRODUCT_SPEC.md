# Product Spec

## v1 Scope

PadMax Pro v1 focuses on real PC games:

- Windows: virtual Xbox 360 controller via ViGEmBus / Nefarius.ViGEm.Client.
- Linux: evdev/uinput virtual controller.
- Android: native virtual gamepad UI, sensors, LAN transport, Bluetooth HID gamepad mode.

## Latency budget

| Stage | Target |
| --- | ---: |
| Touch event to state update | < 2 ms |
| Send loop interval | 8.33 ms at 120 Hz |
| UDP transit on 5 GHz LAN | 1-5 ms typical |
| Server packet parse + virtual device update | < 1 ms |

## Release blockers before app-store launch

- Verify Android app on Samsung, Xiaomi, Pixel, OnePlus, Sony, Oppo/Vivo.
- Verify Windows server on Windows 10/11 with Steam, Xbox app, Epic, emulators.
- Add signed installer and firewall rule management.
- Add crash reporting and anonymous opt-in latency telemetry.
- Add full custom layout editor and cloud layout sync.

## Console support policy

PC replacement is robust through virtual drivers. Consoles are harder because PS/Xbox/Switch do not generally allow arbitrary software on a phone to become a trusted native controller. Android Bluetooth HID mode may work with some HID-capable hosts, but official PS/Xbox console game support usually requires licensed controller authentication or official Remote Play. For console-grade support, add an optional hardware bridge in v2.
