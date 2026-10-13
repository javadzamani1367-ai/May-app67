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
    val EQUIPMENT = mapOf(
        "main_switch" to R.string.equipment_main_switch,
        "fuse_switch" to R.string.equipment_fuse_switch,
        "nh_fuse" to R.string.equipment_nh_fuse,
        "breaker" to R.string.equipment_breaker,
        "busbar" to R.string.equipment_busbar,
        "panel_wiring" to R.string.equipment_panel_wiring,
        "cable_lug" to R.string.equipment_cable_lug,
        "panel_body" to R.string.equipment_panel_body,
        "transformer" to R.string.equipment_transformer,
        "lv_bushing" to R.string.equipment_lv_bushing,
        "cutout" to R.string.equipment_cutout,
        "cutout_fuse" to R.string.equipment_cutout_fuse,
        "arrester" to R.string.equipment_arrester,
        "feeder_cable" to R.string.equipment_feeder_cable,
        "lv_line" to R.string.equipment_lv_line,
        "connector" to R.string.equipment_connector,
        "service_cable" to R.string.equipment_service_cable,
        "service_breaker" to R.string.equipment_service_breaker,
        "meter" to R.string.equipment_meter,
        "meter_box" to R.string.equipment_meter_box
    )
    val PHASES = mapOf(
        "r" to R.string.phase_r,
        "s" to R.string.phase_s,
        "t" to R.string.phase_t,
        "n" to R.string.phase_n
    )
    val CAUSES = mapOf(
        "overload_crypto" to R.string.cause_overload_crypto,
        "overload_illegal" to R.string.cause_overload_illegal,
        "wrong_fuse" to R.string.cause_wrong_fuse,
        "loose_connection" to R.string.cause_loose_connection,
        "phase_imbalance" to R.string.cause_phase_imbalance,
        "weather" to R.string.cause_weather,
        "aging" to R.string.cause_aging,
        "unknown" to R.string.cause_unknown
    )
    val OCCURRED = mapOf(
        "just_now" to R.string.occurred_just_now,
        "today" to R.string.occurred_today,
        "yesterday" to R.string.occurred_yesterday,
        "older" to R.string.occurred_older
    )
}
