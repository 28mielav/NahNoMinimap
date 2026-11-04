package ao1roh.dev.nahnominimap.common.network;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class WireCodec {
    public static final short MAX_STRING_LENGTH = 32767;
    private static final int MAX_ENCODED_LENGTH = MAX_STRING_LENGTH * 4;

    private WireCodec() {}

    public static void writeVarInt(int value, ByteArrayDataOutput output) {
        int remaining = value;
        for (int index = 0; index < 5; index++) {
            int next = remaining & 0x7f;
            remaining >>>= 7;
            output.writeByte(remaining == 0 ? next : next | 0x80);
            if (remaining == 0) return;
        }
        throw new IllegalArgumentException("VarInt is too long");
    }

    public static int readVarInt(ByteArrayDataInput input) {
        int result = 0;
        for (int index = 0; index < 5; index++) {
            int next = input.readUnsignedByte();
            if (index == 4 && (next & 0xf0) != 0) {
                throw new IllegalArgumentException("VarInt exceeds 32 bits");
            }
            result |= (next & 0x7f) << (index * 7);
            if ((next & 0x80) == 0) return result;
        }
        throw new IllegalArgumentException("VarInt is too long");
    }

    public static void writeUtf(String value, ByteArrayDataOutput output) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_LENGTH) throw new IllegalArgumentException("Encoded string exceeds limit");
        writeVarInt(bytes.length, output);
        output.write(bytes);
    }

    public static String readUtf(ByteArrayDataInput input) {
        int length = readVarInt(input);
        if (length < 0 || length > MAX_ENCODED_LENGTH) throw new IllegalArgumentException("Invalid encoded string length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (value.length() > MAX_STRING_LENGTH) throw new IllegalArgumentException("Decoded string exceeds limit");
            return value;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8", e);
        }
    }
}
