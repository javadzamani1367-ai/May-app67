package ir.ilam.inspection.field.ui.report

import ir.ilam.inspection.field.R

/**
 * The Persian label of every checklist key. Explicit maps rather than looking
 * names up at run time: the release build strips strings nothing references,
 * and a looked-up name would quietly come back empty.
 */
object ReportLabels {
    val SIGNS = mapOf(
        "fan_noise" to R.string.sign_fan_noise,
        "heat_exhaust" to R.string.sign_heat_exhaust,
        "covered_windows" to R.string.sign_covered_windows,
        "heavy_cabling" to R.string.sign_heavy_cabling,
        "new_transformer" to R.string.sign_new_transformer,
        "network_gear" to R.string.sign_network_gear,
        "night_activity" to R.string.sign_night_activity,
        "neighbour_report" to R.string.sign_neighbour_report,
        "thermal_hot_pole" to R.string.sign_thermal_hot_pole,
        "voltage_drops" to R.string.sign_voltage_drops
    )
    val CONSUMERS = mapOf(
        "air_conditioner" to R.string.consumer_air_conditioner,
        "heater" to R.string.consumer_heater,
        "water_pump" to R.string.consumer_water_pump,
        "industrial_motor" to R.string.consumer_industrial_motor,
        "welder" to R.string.consumer_welder,
        "furnace" to R.string.consumer_furnace
    )
    val USAGES = mapOf(
        "residential" to R.string.usage_residential,
        "commercial" to R.string.usage_commercial,
        "industrial" to R.string.usage_industrial,
        "agri_well" to R.string.usage_agri_well,
        "agri_greenhouse" to R.string.usage_agri_greenhouse,
        "agri_poultry" to R.string.usage_agri_poultry,
        "agri_livestock" to R.string.usage_agri_livestock,
        "construction" to R.string.usage_construction,
        "other" to R.string.usage_other
    )
    val VIOLATIONS = mapOf(
        "no_meter" to R.string.violation_no_meter,
        "meter_bypass" to R.string.violation_meter_bypass,
        "meter_tamper" to R.string.violation_meter_tamper,
        "borrowed_supply" to R.string.violation_borrowed_supply,
        "wrong_usage" to R.string.violation_wrong_usage
    )
    val TEAM_QUESTIONS = mapOf(
        "vehicle_access" to R.string.team_vehicle_access,
        "needs_ladder" to R.string.team_needs_ladder,
        "needs_police" to R.string.team_needs_police,
        "guarded" to R.string.team_guarded,
        "active_now" to R.string.team_active_now,
        "safety_hazard" to R.string.team_safety_hazard,
        "people_seen" to R.string.team_people_seen
    )
    val BEST_TIMES = mapOf(
        "morning" to R.string.time_morning,
        "afternoon" to R.string.time_afternoon,
        "night" to R.string.time_night
    )
}
