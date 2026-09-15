package dev.moonaticks.customGuiReworked.api.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Тесты {@link DesignAnimation#stageForProgress(int, int, int)} — стрелка прогресса. */
class DesignAnimationStageTest {

    @Test
    void progressZeroOrNegativeGivesFirstFrame() {
        assertEquals(0, DesignAnimation.stageForProgress(0, 200, 4));
        assertEquals(0, DesignAnimation.stageForProgress(-5, 200, 4));
    }

    @Test
    void progressOneGivesFirstFrame() {
        assertEquals(0, DesignAnimation.stageForProgress(1, 200, 4));
    }

    @Test
    void midProgressMatchesPotFormula() {
        // Формула котелка: stage1based = 1 + (cook - 1) * stages / total
        // 0-based: (cook - 1) * stages / total
        assertEquals(0, DesignAnimation.stageForProgress(50, 200, 4));
        assertEquals(1, DesignAnimation.stageForProgress(51, 200, 4));
        assertEquals(2, DesignAnimation.stageForProgress(101, 200, 4));
        assertEquals(3, DesignAnimation.stageForProgress(151, 200, 4));
    }

    @Test
    void fullProgressGivesLastFrame() {
        assertEquals(3, DesignAnimation.stageForProgress(200, 200, 4));
    }

    @Test
    void overProgressClampsToLastFrame() {
        assertEquals(3, DesignAnimation.stageForProgress(500, 200, 4));
    }

    @Test
    void degenerateArgumentsGiveFirstFrame() {
        assertEquals(0, DesignAnimation.stageForProgress(10, 0, 4));
        assertEquals(0, DesignAnimation.stageForProgress(10, 10, 0));
        assertEquals(0, DesignAnimation.stageForProgress(10, 10, -2));
    }

    @Test
    void singleStageAlwaysZero() {
        assertEquals(0, DesignAnimation.stageForProgress(1, 100, 1));
        assertEquals(0, DesignAnimation.stageForProgress(100, 100, 1));
    }
}
