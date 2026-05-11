package uk.hotten.herobrine.world.data;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class MapData {

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String builder;

    // Sentinel NaN means "unset" so the admin map setup wizard can flag it as
    // missing
    // instead of silently using a default that may not match the map's geometry.
    @Getter
    @Setter
    private double shardMin = Double.NaN;

    @Getter
    @Setter
    private double shardMax = Double.NaN;

    @Getter
    @Setter
    private List<Datapoint> datapoints;

    public MapData(String name, String builder, double shardMin, double shardMax, List<Datapoint> datapoints) {

        this.name = name;
        this.builder = builder;
        this.shardMin = shardMin;
        this.shardMax = shardMax;
        this.datapoints = datapoints;

    }

    public MapData() {

    }

}
