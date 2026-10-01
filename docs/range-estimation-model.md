# Proposed range estimation model: exclude prolonged stationary consumption

Status: released in 3.19.3-kom.14 (temperature- and climate-aware model, see the last sections); first implemented in 3.19.3-kom.9. Recorded 2026-10-01, updated 2026-10-02.
Scope: Kom-BYDMate automatic range prediction. Manual calculation remains a separate mode.

## Problem and objective

Several hours of stationary testing can consume energy without adding distance. Including that energy in recent-trip kWh/100 km makes the predicted driving range collapse.
The existing automatic model blends the last three completed trips (50/30/20 weights) with a live-session average. Both sources need stationary-aware accounting.
Objective: learn expected driving consumption, including ordinary traffic stops, while separating prolonged stationary energy use.
Stationary energy is real: retain it in total consumption and let the reduced remaining battery energy reduce range.
The vehicle's native range is a comparison signal, not the replacement prediction or a calibration target.

## Proposed starting parameters

| Parameter | Initial value | Meaning |
|---|---:|---|
| Prolonged stationary threshold | 15 continuous minutes | No confirmed movement for the full interval |
| Learning window | Most recent 100 eligible km | Distance-based history across sessions |
| Minimum learning distance | 5 eligible km | Required before replacing the provisional baseline |

These are proposed configurable defaults, to validate on the vehicle rather than assumed universal values.
Use vehicle speed and odometer together. Genuine slow traffic movement resets the stationary timer; GPS jitter must not.
Gear P supports stationary classification but does not independently establish its duration.
Movement detection thresholds must match observed sensor resolution; do not require a high minimum speed that excludes crawling traffic.

## Stationary accounting

At the last confirmed movement / start of a stationary interval, retain time, odometer, energy-counter baseline, and data-validity state.
Buffer stationary energy provisionally instead of immediately teaching it to the range model.
While stationary, hold the learned driving-consumption estimate; continue updating range from remaining battery energy.
If confirmed movement resumes before 15 minutes, include the buffered energy as an ordinary traffic stop.
If the stationary interval reaches 15 minutes, classify it as prolonged and exclude its energy retroactively from its beginning.
Keep excluding its energy until confirmed movement resumes.
At resumption, establish a new energy baseline so excluded energy cannot leak into the next driving segment.
An exactly 15-minute interval qualifies as prolonged. Even confirmed very slow movement resets the timer.
A completely stationary traffic queue lasting 15 minutes is indistinguishable under this simple rule and will also be excluded; document this limitation.

## Driving consumption and history

For an eligible window:

```text
eligibleEnergyKwh = totalEnergyUsedKwh - prolongedStationaryEnergyKwh
drivingConsumptionKwhPer100 = eligibleEnergyKwh / eligibleDistanceKm * 100
```

Use summed energy and summed distance, not the unweighted mean of individual trip consumption values.
Keep both short-stop energy and real driving accessory loads (e.g. climate control) in eligible energy.
Define energy-counter semantics before implementation: a gross-consumption counter and net battery-energy use are not interchangeable.
Preserve valid regenerative recovery when using net energy. Counter resets, sentinel values, impossible jumps and recalibrations are data-quality events, not evidence of zero consumption.
Do not learn from charging or external discharge sessions, invalid distance, or unobserved energy intervals.
Persist eligible segments separately from ordinary trip totals and retain the latest 100 eligible km across ignition cycles.
Trim at the window boundary using interval detail where available; never invent missing stationary splits from a whole-trip aggregate.
Use the distance-weighted rolling window as the initial smoothing mechanism. Additional smoothing is a later tuning choice, not a second mandatory formula.

## Remaining energy and range

Prefer validated, fresh BMS remaining-energy data:
```text
rangeKm = remainingUsableEnergyKwh / drivingConsumptionKwhPer100 * 100
```

A read-only probe on this vehicle returned remaining energy from Power.POWER_BATTERY_REMAIN_ELECTRICITY (device 1005, fid 882901008, float read / tx 7).
Validate its units, freshness and usable-energy meaning across charge levels before making it the production source.
If unavailable, use a clearly labelled fallback based on verified usable capacity and SOC; do not silently equate nominal capacity with usable capacity.
Never subtract stationary energy again from BMS remaining energy or SOC: that would double-count the loss.
Any configured arrival reserve must be explicit and reflected in the range label.

## Initialization and existing data

Do not delete historical trips or change their total energy accounting.
Existing aggregate trips without stationary interval detail cannot be cleaned exactly. Exclude them from the new learned range history.
Until at least 5 eligible km are available, use the last valid learned baseline, or a provisional vehicle lifetime average if available.
The observed lifetime average of 18.8 kWh/100 km is a diagnostic starting point, not a hard-coded default or a proven stationary-free value.
Persist the baseline and pending stationary classification across process restarts. A missing-data gap cannot prove continuous immobility; mark the interval uncertain and avoid learning from its unobserved portion.
Do not substitute the native 605-km rating or native displayed range for learned real-world consumption.

## Diagnostic example (measurement, not a guarantee)

At the observed 68% SOC, BMS remaining energy was about 51.6 kWh and the vehicle lifetime average was about 18.8 kWh/100 km:

```text
51.6 / 18.8 * 100 = 274.5 km
```

This provisional calculation does not reconstruct or exactly remove past stationary energy.

## Display and diagnostics

Distinguish total average consumption from the driving-consumption estimate used for range.
Show whether the estimate is provisional, learned, using an energy fallback, or unavailable.
Log remaining-energy source, learned consumption, eligible window km/kWh, excluded stationary kWh, stationary duration, and data-quality status.
Native range may be shown separately for comparison.

## Acceptance scenarios for implementation

- Four hours stationary: no new distance learned; all energy in that stationary interval excluded from range consumption, including the first 15 minutes.
- Remaining energy decreases while parked: range decreases proportionally, with no rise in learned driving consumption.
- Short traffic stops: stationary energy included when movement resumes before the threshold.
- Crawling traffic: confirmed movement resets the stationary timer, even at low speed.
- Resume after prolonged stop: no excluded energy carried into the next segment.
- Ignition-off or process restart: pending accounting and learned history recovered without falsely treating gaps as observed parking.
- Charging, counter reset, odometer regression, sensor noise and stale BMS readings: no contaminated history learned.
- Legacy trip totals remain unchanged; new range history does not ingest uncleanable aggregate trips.
- Fallback and provisional status remain visible when BMS energy or eligible history is insufficient.

## Implementation notes for 3.19.3-kom.9

- `DrivingRangeModel` owns separate range history, persisted by `DrivingRangeSource`; ordinary trip totals and the manual temperature table keep their existing accounting.
- Initial parameters are fixed constants in this build: 15 minutes, 100 km, and 5 km. History is committed in approximately 1-km blocks; the oldest boundary block is trimmed proportionally.
- Until enough eligible distance is learned, capture and hold a valid vehicle lifetime average. If that read is unavailable, use the explicitly provisional 18 kWh/100 km default.
- Speed above zero or an odometer increase above 0.001 km confirms motion. Missing/invalid samples, gaps over 60 seconds, and session changes re-anchor the counters instead of learning unseen consumption.
- The BMS value is taken from the current telemetry sample with finite/range checks; SOC/capacity remains the labelled fallback. Independent sensor timestamps and usable-energy calibration are still vehicle-validation work.
- The new consumption history starts empty. It cannot retroactively repair past aggregate trips. The dashboard labels provisional/learned estimates and displays the consumption actually used, including manual-mode estimates.
- Unit tests cover long/short stops, threshold timing, crawling, rolling history, counter discontinuities, restart persistence, and BMS/fallback calculations. On-car validation of counter semantics, sensor resolution, and achievable range remains pending.

## First vehicle-validation APK (2026-10-01)

- Created and verified `Kom-BYDMate-v3.19.3-kom.9-test.apk`, application ID `kom.bydmate`, versionCode `49609`.
- This is a signed, debuggable test build using `komFast` and a temporary init script at `app/build/kom-test-dex.init.gradle`; the normal release build type in the project remains unchanged. The non-debuggable D8 path showed very large intermediate allocations and did not finish; its root cause remains unconfirmed.
- Optional lifetime-baseline initialization now runs in a separate coroutine, with one request in flight, so telemetry sampling does not wait for it.
- The final source passed 145 calculator/dashboard unit tests with zero failures/errors. APK v2 signing was verified; the signer matches kom.8. Vehicle validation is still pending.
- APK SHA-256: `31ba22a385a9ec92f9c5f046906f8e03bfba32466345c83a24e3a2219f3bf442`.

## Temperature- and climate-aware estimate (2026-10-01, after the first test APK)

The 100-km average above stays the long-term learning signal (`DrivingRangeModel.average()`); the range now uses `rangeEstimate()`:

- Temperature prior: every committed 1-km block is also added to its exterior-temperature band (5 °C wide, keyed by `floor(t / 5)`). A band needs 10 km before it is trusted; beyond 500 km it is scaled down, keeping its average while still adapting. Missing or implausible temperature, or a thin band, falls back to the 100-km average (and that to the baseline). Bands persist across seasons, so a cold morning reuses last winter's consumption.
- Reactive window: the last 25 eligible km, restarted when nothing was committed for an hour (ignition-off overnight, a long park). It reflects current AC/heat load, hills and wind.
- Blend by reactive distance: below 5 km the prior alone, 5–20 km linear, above 20 km the reactive window alone.
- Climate is not modelled as a separate power term: it is already in measured energy, and the band carries its typical seasonal share.
- Consumption floor 8 kWh/100 km (`C_FLOOR`; `SANE_AVG` now starts there). The reactive value is clamped to it so a regen-heavy descent cannot extrapolate an absurd range.
- BMS cross-check: BMS remaining energy outside 0.70–1.15 × (SOC × capacity) is treated as a glitch and the SOC × capacity − carry path is used. Skipped at 0% SOC or without a sane capacity setting.
- A charge (gun state 2–5) restarts `excludedKwh`, which therefore reports parked loss since the last charge.
- Persistence writes when learned history or the stop classification changes, otherwise at most once a minute. New JSON fields are optional, so the first build's history still loads.
- Dashboard: the range tile shows the consumption used, the reactive "right now" value once 5 km are driven, and names the temperature band while it still leads. A single poll without SOC no longer blanks the range; a lost link or a stopped service clears it.
- Charging is never consumption: from the moment a charger is detected, everything the counter does is dropped outright (the anchor is released and a pre-plug pending stop is discarded), not averaged in. Detection is gun state 2–5, or, for firmwares that do not report it, standstill with power below −1 kW (energy flowing into the pack; regen needs motion).
