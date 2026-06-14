package net.davidgf.elremote;

/** Electrolux Chill Pro Flex portable AC — IR protocol (13 bytes, LSB-first). */
public final class IrCodec {

    public static final String[] MODES  = {"AUTO", "COOL", "DRY", "FAN"};
    public static final String[] SPEEDS = {"AUTO", "LOW", "MID", "HIGH"};

    // byte11 last-action codes
    public static final int ACT_TEMP = 0x01, ACT_SWING = 0x02, ACT_FAN = 0x04,
                            ACT_POWER = 0x05, ACT_MODE = 0x06, ACT_TIMER = 0x0D,
                            ACT_SETTLED = 0x00;

    // Protocol field values, indexed by the UI's MODES/SPEEDS order.
    private static final int[] MODE_VAL = {0, 1, 2, 6};   // auto, cool, dry, fan
    private static final int[] FAN_VAL  = {5, 3, 2, 1};   // auto, low, mid, high

    public static final class State {
        public boolean power;
        public int mode;   // index into MODES
        public int speed;  // index into SPEEDS
        public int temp;   // 16..32
        public boolean swing;
        public boolean timerEnabled;
        public int timerHours;   // 0..24
        public int timerMins;    // 0 or 30
    }

    /** Build the 13-byte frame for the current state and the button just pressed. */
    public static byte[] encode(State s, int action) {
        int tempField;
        switch (s.mode) {
            case 2:  tempField = 14; break;        // DRY: temp field ignored, reads 14
            case 3:  tempField = 0;  break;        // FAN: reads 0
            default: tempField = s.temp - 8;       // AUTO/COOL: °C - 8
        }

        byte[] b = new byte[13];
        b[0]  = (byte) 0xC3;
        b[1]  = (byte) ((s.swing ? 0 : 7) | ((tempField & 0x1F) << 3));
        b[2]  = (byte) 0xE0;
        b[3]  = 0x00;
        b[4]  = (byte) ((FAN_VAL[s.speed] << 5) | (s.timerHours & 0x1F));  // bits 0-4 = timer hours
        b[5]  = (byte) (s.timerMins & 0xFF);       // timer minutes (0 or 30)
        b[6]  = (byte) (MODE_VAL[s.mode] << 5);    // bit 2 = sleep (unused)
        b[7]  = 0x00;
        b[8]  = 0x00;
        int b9 = s.power ? 0x20 : 0x00;            // bit 5 = power
        if (s.timerEnabled) b9 |= s.power ? 0x40 : 0x80;  // ON->off-timer(bit6), OFF->on-timer(bit7)
        b[9]  = (byte) b9;
        b[10] = 0x00;
        b[11] = (byte) action;

        int sum = 0;
        for (int i = 0; i < 12; i++) sum += b[i] & 0xFF;
        b[12] = (byte) (sum & 0xFF);
        return b;
    }

    // --- IR timing (microseconds), pulse-distance, 38 kHz carrier ---
    public static final int CARRIER_HZ = 38000;
    private static final int HDR_MARK = 8955, HDR_SPACE = 4500;
    private static final int BIT_MARK = 550, ZERO_SPACE = 550, ONE_SPACE = 1700;

    /** Bytes -> ConsumerIrManager pattern (alternating mark/space us). LSB-first. */
    public static int[] toPattern(byte[] data) {
        int[] p = new int[2 + data.length * 16 + 1];
        int i = 0;
        p[i++] = HDR_MARK;
        p[i++] = HDR_SPACE;
        for (byte by : data) {
            for (int bit = 0; bit < 8; bit++) {
                p[i++] = BIT_MARK;
                p[i++] = ((by >> bit) & 1) == 1 ? ONE_SPACE : ZERO_SPACE;
            }
        }
        p[i] = BIT_MARK;
        return p;
    }

    private IrCodec() {}
}

