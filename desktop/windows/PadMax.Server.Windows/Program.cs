using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using PadMax.Server.Windows;
using QRCoder;

const int ControlPort = 28550;
const int DiscoveryPort = 28551;

bool allowPlain = args.Contains("--plain", StringComparer.OrdinalIgnoreCase);

var sessionPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PadMax", "session.json");
SessionData session;
try { session = JsonSerializer.Deserialize<SessionData>(File.ReadAllText(sessionPath)) ?? NewSession(); }
catch { session = NewSession(); }
SaveSession(sessionPath, session);

string pairCode = session.PairCode;
byte[] salt = Convert.FromBase64String(session.Salt);
byte[] key = PacketCodec.DeriveKey(pairCode, salt);
string serverId = session.ServerId;
string serverName = Environment.MachineName + " PadMax";
using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, e) => { e.Cancel = true; cts.Cancel(); };

bool usbReady = TryAdbReverse();

Console.WriteLine("PadMax Server for Windows");
Console.WriteLine($"Control UDP : {ControlPort}");
Console.WriteLine($"Control TCP : {ControlPort} (USB / adb reverse)");
Console.WriteLine($"Discovery UDP: {DiscoveryPort}");
Console.WriteLine($"Mode         : {(allowPlain ? "plain+secure" : "secure only")} on LAN, plain on USB");
Console.WriteLine($"Pair code    : {pairCode}");
Console.WriteLine($"USB (adb)    : {(usbReady ? "reverse tunnel ready" : "not available - plug device with USB debugging on")}");
Console.WriteLine($"Session file : {sessionPath}");
Console.WriteLine("Press Ctrl+C to quit.\n");

var lanIps = LanIpv4();
if (lanIps.Length == 0)
{
    Console.WriteLine("[qr] no LAN IPv4 address found. Connect Wi-Fi/Ethernet, then restart for a scan code.\n");
}
else
{
    Console.WriteLine($"Scan this QR in the Android app to connect over Wi-Fi ({lanIps[0]}):");
    PrintConnectQr(lanIps[0], ControlPort, pairCode, salt, serverName);
    if (lanIps.Length > 1) Console.WriteLine("Other local IPs: " + string.Join(", ", lanIps.Skip(1)) + "\n");
}

using var hub = new ControllerHub();
var discoveryTask = Task.Run(() => DiscoveryLoop(cts.Token));
var controlTask = Task.Run(() => ControlLoop(hub, cts.Token));
var tcpTask = Task.Run(() => TcpLoop(hub, cts.Token));
var pruneTask = Task.Run(async () =>
{
    while (!cts.IsCancellationRequested)
    {
        hub.PruneIdle(TimeSpan.FromSeconds(5));
        await Task.Delay(1000, cts.Token).ContinueWith(_ => { });
    }
});

await Task.WhenAny(discoveryTask, controlTask, tcpTask, pruneTask);
cts.Cancel();

async Task DiscoveryLoop(CancellationToken ct)
{
    using var udp = new UdpClient(DiscoveryPort) { EnableBroadcast = true };
    while (!ct.IsCancellationRequested)
    {
        UdpReceiveResult r;
        try { r = await udp.ReceiveAsync(ct); }
        catch (OperationCanceledException) { break; }
        var text = Encoding.ASCII.GetString(r.Buffer);
        if (!text.StartsWith("PMAX_DISCOVERY_V1", StringComparison.Ordinal)) continue;
        var resp = $"PMAX_SERVER_V1|{serverName}|{ControlPort}|1|{Convert.ToBase64String(salt)}|{serverId}";
        var bytes = Encoding.UTF8.GetBytes(resp);
        await udp.SendAsync(bytes, r.RemoteEndPoint, ct);
    }
}

async Task ControlLoop(ControllerHub hub, CancellationToken ct)
{
    using var udp = new UdpClient(ControlPort);
    var received = 0L;
    var rejected = 0L;
    var lastLog = DateTime.UtcNow;
    while (!ct.IsCancellationRequested)
    {
        UdpReceiveResult r;
        try { r = await udp.ReceiveAsync(ct); }
        catch (OperationCanceledException) { break; }
        if (PacketCodec.TryDecode(r.Buffer, key, allowPlain, out var clientId, out var state, out var error))
        {
            received++;
            hub.Update(clientId, state);
        }
        else
        {
            rejected++;
            if (rejected % 60 == 1) Console.WriteLine($"[reject] {r.RemoteEndPoint}: {error}");
        }
        if (DateTime.UtcNow - lastLog > TimeSpan.FromSeconds(3))
        {
            Console.WriteLine($"[stats] ok={received} rejected={rejected}");
            lastLog = DateTime.UtcNow;
        }
    }
}

async Task TcpLoop(ControllerHub hub, CancellationToken ct)
{
    var listener = new TcpListener(IPAddress.Any, ControlPort);
    listener.Start();
    Console.WriteLine($"[usb] TCP control listening on {ControlPort}");
    try
    {
        while (!ct.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await listener.AcceptTcpClientAsync(ct); }
            catch (OperationCanceledException) { break; }
            catch (SocketException) { continue; }
            _ = Task.Run(async () =>
            {
                var ep = client.Client.RemoteEndPoint?.ToString() ?? "usb";
                Console.WriteLine($"[usb] device connected from {ep}");
                try
                {
                    client.NoDelay = true;
                    using var stream = client.GetStream();
                    var buffer = new byte[PacketCodec.PlainPacketBytes];
                    while (!ct.IsCancellationRequested && await ReadExact(stream, buffer, ct))
                    {
                        if (PacketCodec.TryDecode(buffer, key, allowPlain: true, out var clientId, out var state, out _))
                            hub.Update(clientId, state);
                    }
                }
                catch (OperationCanceledException) { }
                catch (Exception ex) { Console.WriteLine($"[usb] {ep} error: {ex.Message}"); }
                finally { client.Dispose(); Console.WriteLine($"[usb] device disconnected {ep}"); }
            }, ct);
        }
    }
    finally { listener.Stop(); }
}

static async Task<bool> ReadExact(NetworkStream stream, byte[] buffer, CancellationToken ct)
{
    var offset = 0;
    while (offset < buffer.Length)
    {
        int read;
        try { read = await stream.ReadAsync(buffer.AsMemory(offset, buffer.Length - offset), ct); }
        catch (OperationCanceledException) { return false; }
        if (read == 0) return false;
        offset += read;
    }
    return true;
}

static bool TryAdbReverse()
{
    try
    {
        var psi = new ProcessStartInfo("adb", $"reverse tcp:{ControlPort} tcp:{ControlPort}")
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        using var p = Process.Start(psi);
        if (p == null) return false;
        p.WaitForExit(3000);
        return p.ExitCode == 0;
    }
    catch { return false; }
}

static string[] LanIpv4()
{
    try
    {
        return NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .Select(u => u.Address)
            .Where(a => a.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a))
            .Select(a => a.ToString())
            .Distinct()
            .OrderByDescending(a => a.StartsWith("192.168.") || a.StartsWith("10.") || a.StartsWith("172."))
            .ToArray();
    }
    catch { return Array.Empty<string>(); }
}

static void PrintConnectQr(string host, int port, string code, byte[] salt, string name)
{
    var payload = $"padmax://connect?h={host}&p={port}&c={code}&s={Convert.ToBase64String(salt)}&n={Uri.EscapeDataString(name)}";
    try
    {
        using var generator = new QRCodeGenerator();
        var data = generator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
        Console.WriteLine(new AsciiQRCode(data).GetGraphic(1));
    }
    catch (Exception ex) { Console.WriteLine("[qr] failed to render: " + ex.Message); }
    Console.WriteLine("Or open/enter: " + payload + "\n");
}

static SessionData NewSession() => new(
    RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6"),
    Convert.ToBase64String(RandomNumberGenerator.GetBytes(16)),
    Convert.ToHexString(RandomNumberGenerator.GetBytes(6)));

static void SaveSession(string path, SessionData data)
{
    try
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, JsonSerializer.Serialize(data));
    }
    catch { }
}

namespace PadMax.Server.Windows
{
    public sealed record SessionData(string PairCode, string Salt, string ServerId);
}
