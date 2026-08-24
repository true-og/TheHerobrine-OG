package uk.hotten.herobrine.stat.trackers;

import java.util.UUID;

import uk.hotten.herobrine.stat.HerobrineScores;
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.stat.StatTracker;

public class PointsTracker extends StatTracker {

    public PointsTracker(StatManager sm) {

        super(sm, "Points", "points", "Your points!");

    }

    // Mirror every in-round award into the plugin-wide cache so hb_score stays
    // live.
    @Override
    public void increment(UUID uuid, int by) {

        super.increment(uuid, by);
        HerobrineScores.add(uuid, by);

    }

}
