package br.com.deh.copiloto.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CoreTest {
    private val offer = Offer(3800, 1.4, 4.0, 12.0, 24.0)
    private val cost = VehicleCost(simplePerKm = 1.0)
    private val policy = Policy()
    private fun route(km: Double = 9.0, min: Double = 15.0) = DrivingRoute(RouteQuery(GeoPoint(-26.9, -48.68), GeoPoint(-26.9, -48.66), 180.0), km, min, listOf(GeoPoint(-26.9, -48.68), GeoPoint(-26.9, -48.66)), "Motor de teste", 1)
    @Test fun completeCycleIncludesEveryCostAndMinute() {
        val a = Assumptions(3.0, 2.0, 9.0, 14.0, 5.0, 1.0, 2.0, 250)
        val p = Economics.evaluate(offer, a, cost.copy(fixedPerHour = 12.0), policy)
        assertEquals(23.4, p.totalKm, 1e-8); assertEquals(54.0, p.totalMin, 1e-8); assertEquals(2590L, p.variableCostCents); assertEquals(1080L, p.fixedCostCents); assertEquals(130L, p.netCents); assertEquals(1.4444444, p.hourly, 1e-6)
    }
    @Test fun nineKmRoadOverridesTwoKmProximityAcrossAllMetrics() {
        val c = VehicleCost(detailed = true, fuelPrice = 6.0, cityKml = 10.0, highwayKml = 15.0, maintenance = .1, tires = 0.0, oil = 0.0, depreciation = 0.0)
        val a = RouteEconomics.applyReposition(Assumptions(), route(2.0, 5.0)); val b = RouteEconomics.applyReposition(a, route())
        val p = Economics.evaluate(offer, a, c, Policy(30.0, 1.0)); val r = Economics.evaluate(offer, b, c, Policy(30.0, 1.0))
        assertEquals(9.0, b.repositionKm, 0.0); assertEquals(22.4, r.totalKm, 1e-6); assertEquals(51.0, r.totalMin, 0.0)
        assertEquals(490L, r.variableCostCents - p.variableCostCents); assertTrue(r.hourly < p.hourly); assertTrue(r.perKm < p.perKm); assertTrue(r.score < p.score); assertTrue(r.destinationScore!! < p.destinationScore!!)
        assertEquals(1000L, RouteEconomics.opportunityCostCents(b, Policy(30.0, 1.0))); assertTrue(b.accessWarning)
    }
    @Test fun roadProvenancePreserved() { val a = RouteEconomics.applyReposition(Assumptions(), route()); assertTrue(a.routeVerified); assertEquals("Motor de teste", a.routeProvider); assertEquals(1, a.routeAsOf); assertTrue(a.straightLineKm!! < 2.1) }
    @Test fun unknownRoadCannotConcludeDestinationScore() { assertNull(Economics.evaluate(offer, Assumptions(), cost, policy).destinationScore) }
    @Test fun verifiedDistanceDoesNotVaryWithWaitScenario() {
        val p = Economics.evaluate(offer, RouteEconomics.applyReposition(Assumptions(), route()), cost, policy)
        assertEquals((3800 - 2240) / 100.0 / (4 + 24 + .5 * (3 + 15 + 5)) * 60, p.highHourly, 1e-6)
    }
    @Test fun detailedConsumptionWeightsLiters() { val c = cost.copy(detailed = true, fuelPrice = 6.0, cityKml = 10.0, highwayKml = 20.0, maintenance = 0.0, tires = 0.0, oil = 0.0, depreciation = 0.0); assertEquals(.45, c.rate(.5), 1e-8) }
    @Test fun brazilianNumbersAndMoneyRounding() { assertEquals(1234.56, decimal("R$ 1.234,56"), .0001); assertEquals(101L, cents(1.005)); assertEquals(-101L, cents(-1.005)) }
    @Test fun scoreHasDocumentedAnchors() { assertEquals(60, Economics.score(1.0)); assertEquals(85, Economics.score(1.5)); assertEquals(100, Economics.score(9.0)); assertEquals(0, Economics.score(-1.0)) }
    @Test fun negativeProfitIsNotGoodScore() { val p = Economics.evaluate(offer.copy(fareCents = 100), Assumptions(), cost, policy); assertTrue(p.netCents < 0); assertEquals(0, p.score) }
    @Test fun strategyChangesConstraint() { val a = Assumptions(); val h = Economics.evaluate(offer, a, cost, Policy(1.0, 100.0, Strategy.HOUR)); val k = Economics.evaluate(offer, a, cost, Policy(1.0, 100.0, Strategy.KM)); assertTrue(h.score > k.score) }
    @Test(expected = IllegalArgumentException::class) fun zeroTripRejected() { offer.copy(tripMin = 0.0).validate() }
    @Test(expected = IllegalArgumentException::class) fun nanRejected() { offer.copy(pickupKm = Double.NaN).validate() }
    @Test(expected = IllegalArgumentException::class) fun returnRequiresDuration() { Assumptions(repositionKm = 9.0, repositionMin = 0.0).validate() }
    @Test(expected = IllegalArgumentException::class) fun impossibleHeadingRejected() { RouteQuery(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0), 360.0) }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinateRejected() { GeoPoint(91.0, 0.0) }
    @Test(expected = IllegalArgumentException::class) fun zeroConsumptionRejected() { cost.copy(cityKml = 0.0).validate() }
    @Test fun smallNormalAccessDoesNotAlert() { assertFalse(route(2.1, 4.0).difficultAccess) }
    @Test fun sameGeographicPointWithLongAccessAlerts() { val r = route().copy(query = RouteQuery(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0))); assertTrue(r.difficultAccess); assertNull(r.accessRatio) }
    private val sample = "UberX\nR$ 38,00\nBusca 4 min (1,4 km)\nViagem 24 min (12 km)\nDestino: Centro\nAceitar"
    @Test fun parserReadsFiveFieldsAndDestination() { val o = OfferParser.parse(sample).offer!!; assertEquals(3800L, o.fareCents); assertEquals(1.4, o.pickupKm, 0.0); assertEquals(24.0, o.tripMin, 0.0); assertEquals("Centro", o.destination); assertEquals(ReadingQuality.CONTEXTUAL, o.quality) }
    @Test fun parserRejectsIncompleteOffer() { assertNull(OfferParser.parse("R$ 38,00\nBusca 4 min 1,4 km").offer) }
    @Test fun parserRejectsAmbiguousMoney() { assertNull(OfferParser.parse("R$ 28,00\n$sample").offer) }
    @Test fun parserIgnoresBonusAndSaldo() { assertEquals(3800L, OfferParser.parse("Bônus R$ 5,00\nSaldo R$ 120,00\n$sample").offer!!.fareCents) }
    @Test fun parserConvertsMetersAndHours() { val o = OfferParser.parse("R$ 90,00\nBusca 5 min 500 m\nViagem 1 h 10 min 45 km").offer!!; assertEquals(.5, o.pickupKm, 0.0); assertEquals(70.0, o.tripMin, 0.0) }
    @Test fun unlabelledPairsAreMarkedUncertain() { assertEquals(ReadingQuality.ORDERED, OfferParser.parse("R$ 35,00\n4 min 1 km\n20 min 10 km").offer!!.quality) }
    @Test fun personalNameNotUsedAsAddress() { assertEquals("", OfferParser.parse("R$ 35,00\nBusca 4 min 1 km\nMariana Silva\nViagem 20 min 10 km\nJoão").offer!!.destination) }
    @Test fun spatialRowsAreMerged() { val s = OfferParser.merge(listOf(OcrLine("4 min", 10, 10, 50, 30), OcrLine("1,4 km", 80, 12, 150, 32))); assertEquals("4 min 1,4 km", s) }
    @Test fun fewerThanFiveSamplesDoNotInventPrediction() { val now = System.currentTimeMillis(); val a = Learning.estimate(List(4) { ZoneSample("Centro", now, 9.0, 9.0, 9.0) }, "Centro", now, Assumptions()); assertEquals(2.0, a.repositionKm, 0.0); assertEquals(4, a.zoneSamples) }
    @Test fun robustLearningShrinksMedianAndKeepsRoad() { val now = System.currentTimeMillis(); val samples = List(5) { ZoneSample("Centro", now, if (it == 4) 160.0 else 9.0, 9.0, 9.0) }; val a = Learning.estimate(samples, "centro", now, Assumptions()); assertEquals(5.5, a.repositionKm, 0.0); assertEquals(7.0, a.nextOfferWaitMin, 0.0); val road = Learning.estimate(samples, "Centro", now, RouteEconomics.applyReposition(Assumptions(), route(12.0, 22.0))); assertEquals(12.0, road.repositionKm, 0.0); assertEquals(22.0, road.repositionMin, 0.0) }
    @Test fun differentRegionCannotTrainThisRegion() { val now = System.currentTimeMillis(); assertEquals(0, Learning.estimate(List(9) { ZoneSample("Outra", now, 3.0, 3.0, 3.0) }, "Centro", now, Assumptions()).zoneSamples) }
}
