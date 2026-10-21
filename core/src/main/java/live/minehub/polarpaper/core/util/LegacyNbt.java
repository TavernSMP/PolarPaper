package live.minehub.polarpaper.core.util;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagTypes;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class LegacyNbt {
    private LegacyNbt() {
    }

    public static Tag readAnyTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte type = input.readByte();
        return TagTypes.getType(type).load(input, 0, accounter);
    }

    public static Tag readUnnamedTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte type = input.readByte();
        if (type != Tag.TAG_END) {
            input.readUTF();
        }
        return TagTypes.getType(type).load(input, 0, accounter);
    }

    public static void writeAnyTag(Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        tag.write(output);
    }
}
