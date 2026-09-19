package br.com.deh.copiloto.core

import org.junit.Assert.*
import org.junit.Test

class ProductTest {
    @Test fun screenshotFareAndUnitRateOnSameLineRemainDistinct() {
        val text = "99 Pop Nova\nR$32,50 R$1,36/km\n(9 min 2,3 km) Rua Padre Paulo Condla,\n572, São Vicente\n(24 min 21,6 km) Rua Vicente Honorato\nCoelho, 21, Centro\nEscolher"
        val parsed = OfferParser.parse(text)
        assertNotNull(parsed.offer); assertEquals(3250L, parsed.offer!!.fareCents)
        assertEquals(21.6, parsed.offer!!.tripKm, 0.0)
        assertTrue(parsed.offer!!.origin.startsWith("Rua Padre"))
        assertTrue(parsed.offer!!.destination.startsWith("Rua Vicente"))
        assertEquals(ReadingQuality.ORDERED, parsed.offer!!.quality)
    }
    @Test fun previousManualCostSurvivesNewFieldDefaults() { assertEquals(.9, VehicleCost().rate(), 0.0) }
    @Test fun ethanolExampleKeepsPrecision() { assertEquals(4.29 / 9.4, Fuel.perKm(4.29, 9.4), 1e-12); assertEquals(46L, cents(Fuel.perKm(4.29, 9.4))) }
    @Test fun litersPer100ConvertsReciprocally() { assertEquals(12.5, Fuel.kmPerLiter(8.0, ConsumptionUnit.L_100KM), 0.0) }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroConsumption() { Fuel.kmPerLiter(0.0, ConsumptionUnit.KM_L) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNaNConsumption() { Fuel.kmPerLiter(Double.NaN, ConsumptionUnit.KM_L) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInfinitePrice() { Fuel.perKm(Double.POSITIVE_INFINITY, 9.4) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegativePrice() { Fuel.perKm(-4.29, 9.4) }
    @Test fun recognizesBrazilianAverageWithUnit() { val p = PanelParser.parse("Consumo médio\n9,4 km/L\nAutonomia 420 km\n123456 km"); assertTrue(p.unambiguous); assertEquals(9.4, p.candidates.single().kmPerLiter, 0.0) }
    @Test fun recognizesSpacedLitersUnit() { assertEquals(12.5, PanelParser.parse("Média 8,0 L / 100 km").candidates.single().kmPerLiter, 0.0) }
    @Test fun noUnitRequiresManualFallback() { assertTrue(PanelParser.parse("9,4\n420 km\n123456 km").candidates.isEmpty()) }
    @Test fun rejectsInstantaneousConsumption() { assertTrue(PanelParser.parse("Instantâneo 12,2 km/l").candidates.isEmpty()) }
    @Test fun multipleCandidatesRequireChoice() { val p = PanelParser.parse("Média A 9,4 km/l\nMédia B 10,2 km/l"); assertFalse(p.unambiguous); assertEquals(2, p.candidates.size) }
    @Test fun invalidConsumptionNotSuggested() { assertTrue(PanelParser.parse("0,0 km/L\n99,9 km/L").candidates.isEmpty()) }
    @Test fun repeatedIdenticalCandidatesCollapse() { assertEquals(1, PanelParser.parse("9,4 km/L\n9,4 km/l").candidates.size) }
    @Test fun updateKeepsDetailedReservesAndOriginalImmutable() { val old = VehicleCost(detailed = true, maintenance = .2); val next = Fuel.update(old, 4.29, 9.4); assertEquals(old.maintenance, next.maintenance, 0.0); assertEquals(6.2, old.fuelPrice, 0.0); assertEquals(4.29 / 9.4 + .2 + .04 + .03 + .09, next.rate(), 1e-8) }
    @Test fun simpleCostUsesConfirmedFuel() { val next = Fuel.update(VehicleCost(), 4.29, 9.4); assertEquals(4.29 / 9.4, next.rate(), 1e-8) }
    @Test fun internalBackReturnsToParent() { val stack = ProductNavigation.open(ProductNavigation.open(listOf(0), 6), 7); assertEquals(listOf(0, 6), ProductNavigation.back(stack)) }
    @Test fun mainTabBackReturnsHome() { assertEquals(listOf(0), ProductNavigation.back(ProductNavigation.open(listOf(0, 6, 7), 3))) }
    @Test fun repeatedNavigationDoesNotDuplicate() { assertEquals(listOf(0, 7), ProductNavigation.open(listOf(0, 7), 7)) }
    @Test fun rootBackRemainsRoot() { assertEquals(listOf(0), ProductNavigation.back(listOf(0))) }
    @Test fun goalEtaUsesObservedPace() { val p = GoalMath.calculate(100.0, 250.0, 120.0, 180.0); assertEquals(50.0, p.hourly!!, 0.0); assertEquals(180.0, p.minutesRemaining!!, 0.0); assertEquals(50.0, p.requiredHourly!!, 0.0) }
    @Test fun goalNeverProjectsNegativeEarnings() { assertNull(GoalMath.calculate(-20.0, 250.0, 120.0).minutesRemaining) }
    @Test fun insufficientTimeDoesNotProjectGoal() { assertNull(GoalMath.calculate(10.0, 250.0, 10.0).minutesRemaining) }
    @Test fun completedGoalHasZeroRemaining() { val p = GoalMath.calculate(300.0, 250.0, 120.0); assertEquals(0.0, p.remaining, 0.0); assertEquals(1f, p.fraction, 0f) }
    @Test fun noFutureDeadlineProducesNoRequiredRate() { assertNull(GoalMath.calculate(100.0, 250.0, 120.0, -20.0).requiredHourly) }
    @Test fun snapshotSessionCostIncludesIdleTime() { val s = ProductMetrics.summary(emptyList(), listOf(OnlineInterval(0, 3_600_000, 10.0)), 0, 3_600_000); assertEquals(-1000L, s.net); assertEquals(60.0, s.onlineMinutes, 0.0) }
    @Test fun overlappingSessionsDoNotDoubleCount() { val s = ProductMetrics.summary(emptyList(), listOf(OnlineInterval(0, 3_600_000, 10.0), OnlineInterval(1_800_000, 5_400_000, 10.0)), 0, 5_400_000); assertEquals(90.0, s.onlineMinutes, 0.0); assertEquals(1500L, s.costs) }
    @Test fun sessionsClipAtDayBoundary() { val s = ProductMetrics.summary(emptyList(), listOf(OnlineInterval(0, null, 10.0)), 3_600_000, 7_200_000); assertEquals(60.0, s.onlineMinutes, 0.0) }
    @Test fun historicalRideCostsNeverUseCurrentSettings() { val r = ObservedCycle(100, 3000, 500, 100, 10.0, 8.0, 30.0, 20.0, "Centro"); val s = ProductMetrics.summary(listOf(r), emptyList(), 0, 1000); assertEquals(2400L, s.net); assertEquals(48.0, s.hourly, 0.0) }
    @Test fun legacySessionsUseRecordedFixedCosts() { val r = ObservedCycle(100, 3000, 500, 100, 10.0, 8.0, 30.0, 20.0, "Centro"); assertEquals(600L, ProductMetrics.summary(listOf(r), listOf(OnlineInterval(0, 1000, null)), 0, 1000).costs) }
    @Test fun marketNeedsBothWindows() { assertNull(ProductMetrics.market(List(5) { 1_000_000L to 50 }, 1_000_001)) }
    @Test fun marketMeasuresActualScoreChange() { val now = 10_000_000L; val scores = List(5) { now - 1 to 60 } + List(5) { now - 30 * 60000 to 80 }; val signal = ProductMetrics.market(scores, now)!!; assertEquals(-25.0, signal.changePercent, 1e-8); assertEquals(10, signal.samples) }
    @Test fun marketBoundaryDoesNotDuplicateSamples() { val now = 10_000_000L; val scores = List(5) { now - 25 * 60000 to 80 }; assertNull(ProductMetrics.market(scores, now)) }
    @Test fun calibrationDoesNotInventAccuracy() { assertNull(ProductMetrics.calibration(emptyList())); val c = ProductMetrics.calibration(listOf(42.0 to 37.0, 30.0 to 0.0))!!; assertEquals(17.5, c.absoluteHourlyError, 0.0); assertEquals(5.0 / 37.0 * 100, c.relativeErrorPercent!!, 1e-8) }
    @Test fun screenshotSixKmExitReducesNetAndHourly() {
        val offer = Offer(3250, 2.3, 9.0, 21.6, 24.0)
        val c = Fuel.update(VehicleCost(), 4.29, 9.4)
        val basic = Assumptions(repositionKm = 0.0, repositionMin = 0.0)
        val exit = basic.copy(repositionKm = 6.0, repositionMin = 10.0)
        val p = Economics.evaluate(offer, basic, c, Policy()); val q = Economics.evaluate(offer, exit, c, Policy())
        assertEquals(29.9, q.totalKm, 1e-8); assertEquals(32.5 / 29.9, q.fareCents / 100.0 / q.totalKm, 1e-8)
        assertTrue(q.netCents < p.netCents); assertTrue(q.hourly < p.hourly); assertTrue(q.perKm < p.perKm)
        assertNull(q.destinationScore) // 6 km reported by the user is an estimate, not a verified route.
    }
}
