package eptsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TournamentLogicTest {
    @Test
    void balancesTwoHundredEntrantsAcrossNineMaxTables() {
        List<Integer> tableSizes = EptSimulatorApp.balancedTableSizes(200);

        assertEquals(23, tableSizes.size());
        assertEquals(200, tableSizes.stream().mapToInt(Integer::intValue).sum());
        assertEquals(16, tableSizes.stream().filter(size -> size == 9).count());
        assertEquals(7, tableSizes.stream().filter(size -> size == 8).count());
        assertTrue(tableSizes.stream().allMatch(size -> size >= 8 && size <= 9));
    }

    @Test
    void leavesNoShortRemainderTable() {
        for (int participants = 9; participants <= 500; participants++) {
            List<Integer> tableSizes = EptSimulatorApp.balancedTableSizes(participants);
            int smallest = tableSizes.stream().mapToInt(Integer::intValue).min().orElseThrow();
            int largest = tableSizes.stream().mapToInt(Integer::intValue).max().orElseThrow();

            assertEquals(participants, tableSizes.stream().mapToInt(Integer::intValue).sum());
            assertTrue(largest <= 9);
            assertTrue(largest - smallest <= 1);
        }
    }

    @Test
    void callCostIsCappedByStackAndNeverNegative() {
        assertEquals(450, EptSimulatorApp.chipsToCall(950, 500, 1_000));
        assertEquals(100, EptSimulatorApp.chipsToCall(950, 500, 100));
        assertEquals(0, EptSimulatorApp.chipsToCall(400, 500, 1_000));
    }

    @Test
    void lastOpponentFoldingEndsImmediatelyOnlyWhenHeroFolded() {
        assertTrue(EptSimulatorApp.shouldEndAfterFolds(true, 1));
        assertFalse(EptSimulatorApp.shouldEndAfterFolds(true, 2));
        assertFalse(EptSimulatorApp.shouldEndAfterFolds(false, 1));
    }

    @Test
    void timeBankAwardsAreCappedAndEachChipAddsThirtySeconds() {
        assertEquals(6, EptSimulatorApp.rewardTimeBankChip(5, 10));
        assertEquals(10, EptSimulatorApp.rewardTimeBankChip(10, 10));
        assertEquals(30, EptSimulatorApp.timeBankExtensionSeconds());
    }

    @Test
    void actionTimeoutFoldsOnlyWhenThereIsACallToFace() {
        assertTrue(EptSimulatorApp.actionTimeoutShouldFold(1_000, 200, 900));
        assertFalse(EptSimulatorApp.actionTimeoutShouldFold(200, 200, 900));
        assertFalse(EptSimulatorApp.actionTimeoutShouldFold(1_000, 200, 0));
    }

    @Test
    void showdownHandCategoriesHaveReadableNames() {
        assertEquals("One pair", EptSimulatorApp.handCategoryName(1));
        assertEquals("Three of a kind", EptSimulatorApp.handCategoryName(3));
        assertEquals("Four of a kind", EptSimulatorApp.handCategoryName(7));
        assertEquals("Straight flush", EptSimulatorApp.handCategoryName(8));
    }
}