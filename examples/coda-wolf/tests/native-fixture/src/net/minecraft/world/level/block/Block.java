package net.minecraft.world.level.block;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
public final class Block {
    public final String id;
    public final List<String> tags;
    public Block(String id,String... tags) { this.id=id;this.tags=List.of(tags); }
    public RegistryHolder builtInRegistryHolder() { return new RegistryHolder(tags); }
    public static final class RegistryHolder {
        private final List<String> tags;
        public RegistryHolder(List<String> tags) { this.tags=tags; }
        public Stream<Tag> tags() { return tags.stream().map(x->new Tag(Identifier.parse(x))); }
    }
    public record Tag(Identifier location) {}
}
