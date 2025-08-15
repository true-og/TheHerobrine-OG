package uk.hotten.herobrine.world.data;

import java.util.List;
import lombok.Getter;

public class MapBase {

    @Getter
    private List<String> maps;

    public MapBase(List<String> maps) {

        this.maps = maps;

    }

    public MapBase() {

    }

}
