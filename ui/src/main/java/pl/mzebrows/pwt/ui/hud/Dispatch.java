package pl.mzebrows.pwt.ui.hud;

/**
 * One line the terminal has been told to say: what kind, about whom, with up to two numbers. Held in a
 * fixed pool inside {@link Terminal} and overwritten, never allocated per event.
 */
public final class Dispatch {

    private DispatchKind kind = DispatchKind.MISSION_STARTED;
    private int slot = -1;
    private int a;
    private int b;
    private float bornAt;

    Dispatch() {}

    public DispatchKind kind() {
        return kind;
    }

    /** The player it is about, or -1 when it names nobody - including while there is only one player. */
    public int slot() {
        return slot;
    }

    public int a() {
        return a;
    }

    public int b() {
        return b;
    }

    float bornAt() {
        return bornAt;
    }

    void set(DispatchKind kind, int slot, int a, int b, float bornAt) {
        this.kind = kind;
        this.slot = slot;
        this.a = a;
        this.b = b;
        this.bornAt = bornAt;
    }

    void copyFrom(Dispatch other) {
        kind = other.kind;
        slot = other.slot;
        a = other.a;
        b = other.b;
        bornAt = other.bornAt;
    }

    /** Becomes a line of another kind, keeping who it is about and when it was filed. */
    void become(DispatchKind kind) {
        this.kind = kind;
    }

    boolean repeats(DispatchKind kind, int slot, float now, float window) {
        return kind.coalesce() != DispatchKind.Coalesce.NONE
                && this.kind == kind
                && this.slot == slot
                && now - bornAt < window;
    }

    void merge(int a, int b) {
        switch (kind.coalesce()) {
            case NEWEST -> {
                this.a = a;
                this.b = b;
            }
            case SUM -> this.a += a;
            case NONE -> throw new IllegalStateException(kind + " does not coalesce");
        }
    }
}
