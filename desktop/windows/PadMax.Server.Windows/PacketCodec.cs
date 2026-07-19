using System.Buffers.Binary;
using System.Security.Cryptography;

namespace PadMax.Server.Windows;

public readonly record struct InputState(
    ulong Buttons,
    float Lx, float Ly,
    float Rx, float Ry,
    float Lt, float Rt,
    float AccelX, float AccelY, float AccelZ,
    float GyroX, float GyroY, float GyroZ,
    ulong TimestampNs
);

public static class PacketCodec
{
    public const uint Magic = 0x58414D50;
    public const ushort Version = 1;
    public const ushort TypeState = 0x0003;
    public const ushort TypeStateSecure = 0x1003;
    public const int PayloadBytes = 64;
    public const int PlainPacketBytes = 88;

    public static byte[] DeriveKey(string pairCode, byte[] salt)
    {
        using var kdf = new Rfc2898DeriveBytes(pairCode.Trim(), salt, 120_000, HashAlgorithmName.SHA256);
        return kdf.GetBytes(32);
    }

    public static bool TryDecode(ReadOnlySpan<byte> packet, byte[]? key, bool allowPlain, out ulong clientId, out InputState state, out string error)
    {
        clientId = 0;
        state = default;
        error = string.Empty;
        if (packet.Length < 20) { error = "short packet"; return false; }
        var magic = BinaryPrimitives.ReadUInt32LittleEndian(packet[0..4]);
        var version = BinaryPrimitives.ReadUInt16LittleEndian(packet[4..6]);
        var type = BinaryPrimitives.ReadUInt16LittleEndian(packet[6..8]);
        if (magic != Magic || version != Version) { error = "bad magic/version"; return false; }
        clientId = BinaryPrimitives.ReadUInt64LittleEndian(packet[12..20]);

        if (type == TypeState)
        {
            if (!allowPlain) { error = "plain packet rejected"; return false; }
            if (packet.Length != PlainPacketBytes) { error = "bad plain length"; return false; }
            var expected = BinaryPrimitives.ReadUInt32LittleEndian(packet[(PlainPacketBytes - 4)..PlainPacketBytes]);
            var actual = Crc32.Compute(packet[0..(PlainPacketBytes - 4)]);
            if (actual != expected) { error = "crc mismatch"; return false; }
            state = ReadPayload(packet[20..84]);
            return true;
        }

        if (type == TypeStateSecure)
        {
            if (key == null) { error = "secure packet but key missing"; return false; }
            if (packet.Length < 50) { error = "bad secure length"; return false; }
            var nonce = packet[20..32].ToArray();
            var cipherLen = BinaryPrimitives.ReadUInt16LittleEndian(packet[32..34]);
            if (packet.Length != 34 + cipherLen || cipherLen < 17) { error = "cipher length mismatch"; return false; }
            var cipherAndTag = packet[34..].ToArray();
            var cipher = cipherAndTag.AsSpan(0, cipherAndTag.Length - 16).ToArray();
            var tag = cipherAndTag.AsSpan(cipherAndTag.Length - 16, 16).ToArray();
            if (cipher.Length != PayloadBytes) { error = "payload length mismatch"; return false; }
            var plain = new byte[PayloadBytes];
            try
            {
                using var aes = new AesGcm(key, 16);
                aes.Decrypt(nonce, cipher, tag, plain, null);
                state = ReadPayload(plain);
                return true;
            }
            catch (CryptographicException ex)
            {
                error = "decrypt failed: " + ex.Message;
                return false;
            }
        }

        error = "unknown packet type";
        return false;
    }

    private static InputState ReadPayload(ReadOnlySpan<byte> p)
    {
        ulong ts = BinaryPrimitives.ReadUInt64LittleEndian(p[0..8]);
        ulong buttons = BinaryPrimitives.ReadUInt64LittleEndian(p[8..16]);
        return new InputState(
            buttons,
            ReadF32(p[16..20]), ReadF32(p[20..24]),
            ReadF32(p[24..28]), ReadF32(p[28..32]),
            ReadF32(p[32..36]), ReadF32(p[36..40]),
            ReadF32(p[40..44]), ReadF32(p[44..48]), ReadF32(p[48..52]),
            ReadF32(p[52..56]), ReadF32(p[56..60]), ReadF32(p[60..64]),
            ts
        );
    }

    private static float ReadF32(ReadOnlySpan<byte> s) => BitConverter.Int32BitsToSingle(BinaryPrimitives.ReadInt32LittleEndian(s));
}

public static class Crc32
{
    private static readonly uint[] Table = Enumerable.Range(0, 256).Select(i =>
    {
        uint c = (uint)i;
        for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
        return c;
    }).ToArray();

    public static uint Compute(ReadOnlySpan<byte> data)
    {
        uint crc = 0xFFFFFFFFu;
        foreach (byte b in data) crc = Table[(crc ^ b) & 0xFF] ^ (crc >> 8);
        return ~crc;
    }
}
