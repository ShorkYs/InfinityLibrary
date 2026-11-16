package com.infinitylibrary.engine;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import java.util.Random;

/** New library worlds contain no terrain that can obstruct room expansion. */
public final class VoidLibraryGenerator extends ChunkGenerator {
    @Override public boolean shouldGenerateNoise(WorldInfo w, Random r, int x, int z) { return false; }
    @Override public boolean shouldGenerateSurface(WorldInfo w, Random r, int x, int z) { return false; }
    @Override public boolean shouldGenerateCaves(WorldInfo w, Random r, int x, int z) { return false; }
    @Override public boolean shouldGenerateDecorations(WorldInfo w, Random r, int x, int z) { return false; }
    @Override public boolean shouldGenerateStructures(WorldInfo w, Random r, int x, int z) { return false; }
    @Override public boolean shouldGenerateMobs(WorldInfo w, Random r, int x, int z) { return false; }
}
