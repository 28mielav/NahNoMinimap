package ao1roh.dev.nahnominimap.common.version;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {
    private final int release;
    private final int major;
    private final int minor;

    public MinecraftVersion(int release, int major, int minor) {
        this.release = release;
        this.major = major;
        this.minor = minor;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int first = Integer.compare(release, other.release);
        if (first != 0) return first;
        int second = Integer.compare(major, other.major);
        return second != 0 ? second : Integer.compare(minor, other.minor);
    }

    public boolean greaterThanEqual(MinecraftVersion other) { return compareTo(other) >= 0; }
    public int getRelease() { return release; }
    public int getMajor() { return major; }
    public int getMinor() { return minor; }

    @Override
    public String toString() { return release + "." + major + "." + minor; }
}
