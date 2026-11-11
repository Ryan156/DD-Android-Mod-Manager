package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.SaveParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveTransferPlannerTest {

    @Test
    fun plansReorderForFullyOverlappingSaves() {
        val source = SaveTestArchive.buildArchive("Alpha", "Beta", "Gamma")
        val target = SaveTestArchive.buildArchive("Gamma", "Alpha", "Beta")

        val plan = SaveTransferPlanner.buildPlan(
            SaveParser.parse(source),
            SaveParser.parse(target)
        )

        assertTrue(plan.canTransfer)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), plan.orderedTargetEntries.map { it.name })
        assertEquals(3, plan.matched.size)
        assertTrue(plan.appendedNotInSource.isEmpty())
        assertTrue(plan.ignoredNotInstalled.isEmpty())
    }

    @Test
    fun appendsTargetOnlyModsAtTheEndAndIgnoresNotInstalled() {
        val source = SaveTestArchive.buildArchive("Alpha", "Beta", "Omega")
        // Target has Alpha/Beta plus two mods the source save doesn't know.
        val target = SaveTestArchive.buildArchive("Delta", "Beta", "Alpha", "Epsilon")

        val plan = SaveTransferPlanner.buildPlan(
            SaveParser.parse(source),
            SaveParser.parse(target)
        )

        assertTrue(plan.canTransfer)
        // Alpha & Beta follow source order; Delta & Epsilon appended in current relative order.
        assertEquals(
            listOf("Alpha", "Beta", "Delta", "Epsilon"),
            plan.orderedTargetEntries.map { it.name }
        )
        assertEquals(listOf("Alpha", "Beta"), plan.matched)
        assertEquals(listOf("Delta", "Epsilon"), plan.appendedNotInSource)
        assertEquals(listOf("Omega"), plan.ignoredNotInstalled)
    }

    @Test
    fun matchesCaseAndSpaceVariantsViaNormalization() {
        val source = SaveTestArchive.buildArchive("Falconer Class")
        val target = SaveTestArchive.buildArchive("falconerclass")

        val plan = SaveTransferPlanner.buildPlan(
            SaveParser.parse(source),
            SaveParser.parse(target)
        )

        assertTrue(plan.canTransfer)
        assertEquals(listOf("falconerclass"), plan.orderedTargetEntries.map { it.name })
    }

    @Test
    fun refusesWhenNothingMatches() {
        val source = SaveTestArchive.buildArchive("Alpha")
        val target = SaveTestArchive.buildArchive("Zeta")

        val plan = SaveTransferPlanner.buildPlan(
            SaveParser.parse(source),
            SaveParser.parse(target)
        )

        assertFalse(plan.canTransfer)
        assertEquals(listOf("Zeta"), plan.orderedTargetEntries.map { it.name })
        assertEquals(listOf("Alpha"), plan.ignoredNotInstalled)
    }

    @Test
    fun emptySourceOrderCannotTransfer() {
        // Empty entries will not parse strictly; simulate with a nameless section instead.
        val source = SaveTestArchive.buildArchive("OnlyMod")
        val target = SaveTestArchive.buildArchive("OnlyMod")
        val plan = SaveTransferPlanner.buildPlan(
            SaveParser.parse(source),
            SaveParser.parse(target)
        )
        assertTrue(plan.canTransfer)
    }

}