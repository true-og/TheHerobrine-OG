package uk.hotten.herobrine.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import lombok.Getter;

public enum ShardName {
    FURY("Fury"),
    MALCONTENT("Malcontent"),
    REPUGNANCE("Repugnance"),
    SPITE("Spite");

    @Getter
    private String name;

    private ShardName(String name) {
        this.name = name;
    }

    public static ShardName getRandom() {
        List<ShardName> vals = Arrays.asList(values());
        Random random = new Random();
        return vals.get(random.nextInt(vals.size()));
    }
}
