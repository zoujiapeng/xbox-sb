using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Exceptions;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace PadMax.Server.Windows;

public sealed class ControllerHub : IDisposable
{
    private readonly ViGEmClient? client;
    private readonly bool driverAvailable;
    private readonly Dictionary<ulong, Slot> slots = new();
    private readonly object gate = new();
    private const int MaxControllers = 4;

    public ControllerHub()
    {
        try
        {
            client = new ViGEmClient();
            driverAvailable = true;
        }
        catch (VigemBusNotFoundException)
        {
            driverAvailable = false;
            Console.WriteLine("[controller] ViGEmBus is not installed; running protocol-only test mode.");
        }
    }

    public void Update(ulong clientId, InputState state)
    {
        if (!driverAvailable) return;

        lock (gate)
        {
            if (!slots.TryGetValue(clientId, out var slot))
            {
                if (slots.Count >= MaxControllers) return;
                var pad = client!.CreateXbox360Controller();
                pad.AutoSubmitReport = false;
                pad.Connect();
                slot = new Slot(pad, slots.Count + 1);
                slots[clientId] = slot;
                Console.WriteLine($"[controller] client=0x{clientId:X16} assigned player {slot.PlayerIndex}");
            }
            Apply(slot.Controller, state);
            slot.LastSeenUtc = DateTime.UtcNow;
        }
    }

    public void PruneIdle(TimeSpan idle)
    {
        lock (gate)
        {
            var now = DateTime.UtcNow;
            foreach (var pair in slots.ToArray())
            {
                if (now - pair.Value.LastSeenUtc <= idle) continue;
                Console.WriteLine($"[controller] client=0x{pair.Key:X16} disconnected by idle timeout");
                pair.Value.Controller.Disconnect();
                slots.Remove(pair.Key);
            }
        }
    }

    private static void Apply(IXbox360Controller c, InputState s)
    {
        Set(c, Xbox360Button.A, Has(s.Buttons, 0));
        Set(c, Xbox360Button.B, Has(s.Buttons, 1));
        Set(c, Xbox360Button.X, Has(s.Buttons, 2));
        Set(c, Xbox360Button.Y, Has(s.Buttons, 3));
        Set(c, Xbox360Button.LeftShoulder, Has(s.Buttons, 4));
        Set(c, Xbox360Button.RightShoulder, Has(s.Buttons, 5));
        Set(c, Xbox360Button.Back, Has(s.Buttons, 6));
        Set(c, Xbox360Button.Start, Has(s.Buttons, 7));
        Set(c, Xbox360Button.Guide, Has(s.Buttons, 8));
        Set(c, Xbox360Button.LeftThumb, Has(s.Buttons, 9));
        Set(c, Xbox360Button.RightThumb, Has(s.Buttons, 10));
        Set(c, Xbox360Button.Up, Has(s.Buttons, 11));
        Set(c, Xbox360Button.Down, Has(s.Buttons, 12));
        Set(c, Xbox360Button.Left, Has(s.Buttons, 13));
        Set(c, Xbox360Button.Right, Has(s.Buttons, 14));
        c.SetAxisValue(Xbox360Axis.LeftThumbX, Axis(s.Lx));
        c.SetAxisValue(Xbox360Axis.LeftThumbY, Axis(s.Ly));
        c.SetAxisValue(Xbox360Axis.RightThumbX, Axis(s.Rx));
        c.SetAxisValue(Xbox360Axis.RightThumbY, Axis(s.Ry));
        c.SetSliderValue(Xbox360Slider.LeftTrigger, Trigger(s.Lt));
        c.SetSliderValue(Xbox360Slider.RightTrigger, Trigger(s.Rt));
        c.SubmitReport();
    }

    private static bool Has(ulong b, int bit) => ((b >> bit) & 1UL) != 0;
    private static void Set(IXbox360Controller c, Xbox360Button b, bool pressed) => c.SetButtonState(b, pressed);
    private static short Axis(float v) => (short)Math.Clamp((int)MathF.Round(Math.Clamp(v, -1f, 1f) * short.MaxValue), short.MinValue, short.MaxValue);
    private static byte Trigger(float v) => (byte)Math.Clamp((int)MathF.Round(Math.Clamp(v, 0f, 1f) * byte.MaxValue), 0, 255);

    public void Dispose()
    {
        lock (gate)
        {
            foreach (var s in slots.Values) s.Controller.Disconnect();
            slots.Clear();
            client?.Dispose();
        }
    }

    private sealed class Slot(IXbox360Controller controller, int playerIndex)
    {
        public IXbox360Controller Controller { get; } = controller;
        public int PlayerIndex { get; } = playerIndex;
        public DateTime LastSeenUtc { get; set; } = DateTime.UtcNow;
    }
}
