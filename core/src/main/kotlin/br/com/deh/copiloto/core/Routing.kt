package br.com.deh.copiloto.core

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Coordenada inválida" } }
}
object Geo {
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val la = Math.toRadians(b.latitude - a.latitude); val lo = Math.toRadians(b.longitude - a.longitude)
        val h = sin(la / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lo / 2).pow(2)
        return 6371.0088 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
data class RouteQuery(val from: GeoPoint, val to: GeoPoint, val departureHeading: Double? = null, val curbApproach: Boolean = true, val avoidFerries: Boolean = false) {
    init { require(departureHeading == null || departureHeading.isFinite() && departureHeading >= 0 && departureHeading < 360) }
}
data class RouteCapabilities(val traffic: Boolean = false, val tollPrices: Boolean = false, val geometry: Boolean = true, val turnRestrictions: Boolean = true)
data class TollEstimate(val cents: Long, val currency: String = "BRL", val source: String, val estimatedAt: Long)
data class DrivingRoute(val query: RouteQuery, val distanceKm: Double, val durationMin: Double, val geometry: List<GeoPoint>, val provider: String, val calculatedAt: Long, val maneuvers: List<String> = emptyList(), val roadRefs: List<String> = emptyList(), val highway: Boolean = false, val tollRoad: Boolean? = null, val tollEstimate: TollEstimate? = null, val trafficIncluded: Boolean = false, val cached: Boolean = false, val snapDistanceMeters: Double = 0.0) {
    init { require(distanceKm.isFinite() && distanceKm >= 0 && durationMin.isFinite() && durationMin >= 0 && (distanceKm == 0.0 || durationMin > 0)); require(geometry.size >= 2) }
    val straightLineKm: Double get() = Geo.distance(query.from, query.to)
    val accessRatio: Double? get() = if (straightLineKm >= .05) distanceKm / straightLineKm else null
    val difficultAccess: Boolean get() = distanceKm - straightLineKm >= 1.0 && (accessRatio ?: Double.POSITIVE_INFINITY) >= 1.7
}
sealed interface RoutingResult {
    data class Found(val route: DrivingRoute) : RoutingResult
    data class Unavailable(val reason: String, val retryable: Boolean = true) : RoutingResult
}
interface RoutingProvider {
    val id: String
    val capabilities: RouteCapabilities
    suspend fun calculateRoute(query: RouteQuery): RoutingResult
    suspend fun getDrivingDistance(query: RouteQuery): Double? = (calculateRoute(query) as? RoutingResult.Found)?.route?.distanceKm
    suspend fun getEstimatedDuration(query: RouteQuery): Double? = (calculateRoute(query) as? RoutingResult.Found)?.route?.durationMin
    fun supportsTraffic() = capabilities.traffic
    fun supportsTolls() = capabilities.tollPrices
    fun getTollEstimate(route: DrivingRoute) = route.tollEstimate
    fun getRouteGeometry(route: DrivingRoute) = route.geometry
}
object RouteEconomics {
    fun applyReposition(base: Assumptions, route: DrivingRoute) = base.copy(repositionKm = route.distanceKm, repositionMin = route.durationMin, routeVerified = true, routeProvider = route.provider, routeAsOf = route.calculatedAt, straightLineKm = route.straightLineKm, accessWarning = route.difficultAccess)
    fun opportunityCostCents(a: Assumptions, policy: Policy) = cents((a.repositionMin + a.nextOfferWaitMin) / 60 * policy.minHourly)
    fun destinationScore(o: Offer, a: Assumptions, cost: VehicleCost, policy: Policy): Int? {
        if (!a.routeVerified) return null
        val expenses = cents(a.repositionKm * cost.rate(a.highwayFraction) + (a.repositionMin + a.nextOfferWaitMin) / 60 * cost.fixedPerHour)
        return (100.0 * o.fareCents / (o.fareCents + expenses + opportunityCostCents(a, policy))).roundToInt().coerceIn(0, 100)
    }
}
