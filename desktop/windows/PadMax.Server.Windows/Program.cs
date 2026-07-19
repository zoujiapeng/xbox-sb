using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using PadMax.Server.Windows;

const int ControlPort = 28550;
const int DiscoveryPort = 28551;

bool allowPlain = args.Contains("--plain", StringComparer.OrdinalIgnoreCase);
string pairCode = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
byte[] salt = RandomNumberGenerator.GetBytes(16);
byte[] key = PacketCodec.DeriveKey(pairCode, salt);
string serverId = Convert.ToHexString(RandomNumberGenerator.GetBytes(6));
string serverName = Environment.MachineName + " PadMax";
using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, e) => { e.Cancel = true; cts.Cancel(); };

Console.WriteLine("PadMax Server for Windows");
Console.WriteLine($"Control UDP : {ControlPort}");
Console.WriteLine($"Discovery UDP: {DiscoveryPort}");
Console.WriteLine($"Mode         : {(allowPlain ? "plain+secure" : "secure only")}");
Console.WriteLine($"Pair code    : {pairCode}");
Console.WriteLine("Install ViGEmBus before running games. Press Ctrl+C to quit.\n");

using var hub = new ControllerHub();
var discoveryTask = Task.Run(() => DiscoveryLoop(cts.Token));
var controlTask = Task.Run(() => ControlLoop(hub, cts.Token));
var pruneTask = Task.Run(async () =>
{
    while (!cts.IsCancellationRequested)
    {
        hub.PruneIdle(TimeSpan.FromSeconds(5));
        await Task.Delay(1000, cts.Token).ContinueWith(_ => { });
    }
});

await Task.WhenAny(discoveryTask, controlTask, pruneTask);
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
