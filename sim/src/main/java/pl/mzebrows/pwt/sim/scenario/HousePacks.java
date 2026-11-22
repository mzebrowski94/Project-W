package pl.mzebrows.pwt.sim.scenario;

import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Wander;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A house pack's creatures (G0112): what one standing in a house is, and how it is put on the field. The one place
 * its patch is made the house - centred on the inside, no wider than half its shorter side - and its drift switched
 * off, so it wanders inside until it notices somebody and the shut doors hold it in.
 */
final class HousePacks {

    private HousePacks() {}

    /** One of a house pack, standing at {@code x, y} inside this house. */
    static Placed.Housed in(House house, TileMap map, UnitKind strain, float x, float y, int biome) {
        TileRect inside = house.inside();
        float tile = map.tileSize();
        float homeX = (inside.col() + inside.width() * 0.5f) * tile;
        float homeY = (inside.row() + inside.height() * 0.5f) * tile;
        float roam = Math.min(inside.width(), inside.height()) * tile * 0.5f;
        return new Placed.Housed(strain, x, y, homeX, homeY, roam, biome);
    }

    /** Puts it on the field, unaware as everything a mission puts down is, its patch the house. */
    static Entity spawn(Simulation simulation, Placed.Housed housed) {
        Entity unit = simulation.spawnUnit(housed.strain(), housed.x(), housed.y());
        unit.getComponent(Wander.class).confine(housed.homeX(), housed.homeY(), housed.roam());
        return unit;
    }
}
