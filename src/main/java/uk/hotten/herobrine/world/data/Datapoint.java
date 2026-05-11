package uk.hotten.herobrine.world.data;

import lombok.Getter;
import lombok.Setter;

public class Datapoint {

    @Getter
    @Setter
    private DatapointType type;

    @Getter
    @Setter
    private Integer x;

    @Getter
    @Setter
    private Integer y;

    @Getter
    @Setter
    private Integer z;

    public Datapoint(DatapointType type, int x, int y, int z) {

        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;

    }

    public Datapoint() {

    }

}
