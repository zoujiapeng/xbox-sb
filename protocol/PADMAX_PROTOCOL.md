# PadMax Protocol v1

## Ports

- Discovery UDP: `28551`
- Control UDP: `28550`

## Discovery

Client broadcasts ASCII:

```text
PMAX_DISCOVERY_V1
```

Server replies:

```text
PMAX_SERVER_V1|<server_name>|<control_port>|<secure:0/1>|<salt_base64>|<server_id>
```

If `secure=1`, the Android client must derive an AES-256-GCM key from the server pairing code and the advertised salt.

## State payload

All binary fields use little-endian order.

```c
struct StatePayloadV1 {
  uint64_t timestamp_ns;
  uint64_t buttons;
  float lx;
  float ly;
  float rx;
  float ry;
  float lt;
  float rt;
  float accel_x;
  float accel_y;
  float accel_z;
  float gyro_x;
  float gyro_y;
  float gyro_z;
};
```

Axes are normalized to `[-1.0, +1.0]`. Triggers are normalized to `[0.0, 1.0]`.

## Plain packet

```c
struct PlainStatePacketV1 {
  uint32_t magic;
  uint16_t version;
  uint16_t type;
  uint32_t seq;
  uint64_t client_id;
  StatePayloadV1 payload;
  uint32_t crc32;
};
```

## Secure packet

```c
struct SecureStatePacketV1 {
  uint32_t magic;
  uint16_t version;
  uint16_t type;
  uint32_t seq;
  uint64_t client_id;
  uint8_t nonce[12];
  uint16_t cipher_len;
  uint8_t cipher_and_tag[cipher_len];
};
```

## Key derivation

```text
PBKDF2-HMAC-SHA256(pairing_code_utf8, salt, 120000 iterations, 32 bytes)
```

AES mode: `AES/GCM/NoPadding`, 128-bit authentication tag.
