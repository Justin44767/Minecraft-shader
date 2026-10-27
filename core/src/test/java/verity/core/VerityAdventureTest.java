package verity.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static verity.core.VerityWorldState.Phase;

class VerityAdventureTest {
    final PlayerIdentity host = new PlayerIdentity(UUID.randomUUID(), "Host", true, true, PlayerIdentity.Mode.SURVIVAL);
    final PlayerIdentity guest = new PlayerIdentity(UUID.randomUUID(), "Guest", false, false, PlayerIdentity.Mode.SURVIVAL);
    VerityEngine engine() { return new VerityEngine(new VerityWorldState(UUID.randomUUID()), VerityConfig.defaults(), new ItemCatalog(Set.of(), Map.of())); }

    @Test void storyDoesNotRunInsideTheSealedBox() {
        var e = engine(); e.setMood(host, 0);
        assertEquals(Phase.FRIEND, e.advanceStory(1200, 20)); assertEquals(0, e.storyTime());
    }
    @Test void personalityProgressionRequiresTimeAndActualMood() {
        var e = engine(); e.claimEntity(host, UUID.randomUUID());
        assertEquals(Phase.OBSERVER, e.advanceStory(20, 20));
        assertEquals(Phase.OBSERVER, e.advanceStory(20, 20)); // good mood prevents corruption
        e.setMood(host, 40); assertEquals(Phase.CORRUPTED, e.advanceStory(0, 20));
        e.setMood(host, 0); assertEquals(Phase.DEMON, e.advanceStory(20, 20));
        e.setMood(host, 79); assertEquals(Phase.DEMON, e.advanceStory(0, 20));
        e.setMood(host, 80); assertEquals(Phase.OBSERVER, e.advanceStory(0, 20));
    }
    @Test void flashlightRescueImprovesOnlyTheHelpersPersonalTrust() {
        var e = engine(); e.claimEntity(host, UUID.randomUUID()); e.view(host); e.view(guest);
        e.setPhase(host, Phase.DEMON); e.setMood(host, 0);
        e.lightRescue(guest);
        assertEquals(2, e.worldView().mood()); assertEquals(50, e.view(host).personalTrust()); assertEquals(51, e.view(guest).personalTrust());
    }
    @Test void storyRejectsInvalidClockInputs() {
        var e = engine();
        assertThrows(IllegalArgumentException.class, () -> e.advanceStory(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> e.advanceStory(20, 0));
    }
    @Test void coneLightsForwardAndFallsOffWithDistance() {
        var cone = new FlashlightCone(0, 0, 0, 0, 0, 2, 12);
        assertTrue(cone.luminance(0, 0, 1, 12, 1) > cone.luminance(0, 0, 6, 12, 1));
        assertEquals(0, cone.luminance(0, 0, -1, 12, 1)); assertEquals(0, cone.luminance(0, 0, 13, 13, 1));
        assertEquals(0, cone.luminance(5, 0, 5, 12, 1));
    }
    @Test void coneHasSoftEdgesAndDoesNotReachPastAnOccluder() {
        var cone = new FlashlightCone(0, 0, 0, 0, 0, 1, 12);
        double center = cone.luminance(0, 0, 5, 12, 1), edge = cone.luminance(2, 0, 5, 12, 1);
        assertTrue(edge > 0 && edge < center);
        assertEquals(0, cone.luminance(0, 0, 5, 2, 1));
        assertTrue(cone.luminance(0, 0, 2.5, 2, 1) > 0);
    }
    @Test void coneRejectsInvalidDirectionsAndNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new FlashlightCone(0, 0, 0, 0, 0, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> new FlashlightCone(Double.NaN, 0, 0, 0, 0, 1, 12));
    }
}
