package uk.hotten.herobrine.world;

import java.util.Collections;
import java.util.List;

import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

// Pure-void generator for Herobrine arena worlds.
// Saved chunks (from .mca region files in the source map) still load from disk.
// Anything outside the saved area generates as void instead of the level.dat
// flat default, which surprised admins teleporting in to set up spawn points.
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {

        return false;

    }

    @Override
    public boolean shouldGenerateSurface() {

        return false;

    }

    @Override
    public boolean shouldGenerateCaves() {

        return false;

    }

    @Override
    public boolean shouldGenerateDecorations() {

        return false;

    }

    @Override
    public boolean shouldGenerateMobs() {

        return false;

    }

    @Override
    public boolean shouldGenerateStructures() {

        return false;

    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {

        return Collections.emptyList();

    }

}
