# Satellite Tracker

A production-oriented satellite tracking backend that computes when and where satellites will be visible from a given observer location.

It uses fresh orbital data from CelesTrak, Orekit/SGP4 propagation, visibility heuristics, and optional Telegram notifications.

## Table Of Contents

1. Project Overview
2. Key Features
3. Tech Stack
4. High-Level Architecture
5. Getting Started
6. Configuration
7. Security And Data Exposure
8. API Reference
9. Orbital Computation Flow
10. Time Zones And Date Handling
11. Testing
12. Troubleshooting
13. Suggested Improvements
14. Development Notes

## Project Overview

Satellite Tracker helps you answer practical questions such as:

1. When will the ISS pass over my location?
2. Which satellites are visible in the next few hours?
3. In which direction should I look (azimuth/cardinal direction)?
4. How good are viewing conditions (night/twilight/daylight, sunlit, estimated magnitude)?

The backend exposes REST APIs and is designed to run with Docker Compose.

## Key Features

1. Satellite catalog and orbital history retrieval.
2. Pass prediction for single satellite and bulk upcoming passes.
3. Custom observer location support.
4. City-name lookup (geocoding) to coordinates.
5. Visibility classification and photometry estimation.
6. Cache for upcoming pass computations.
7. Telegram notification integration.
8. ISS quick access by satellite name (no numeric ID required).

## Tech Stack

1. Java 17
2. Spring Boot 4
3. Spring Data JPA + Hibernate
4. PostgreSQL 15
5. Orekit 12.1 + Hipparchus 3.1
6. Docker + Docker Compose
7. JUnit 5 + Mockito

## High-Level Architecture

Main modules after refactor:

1. Controllers
1. SatelliteQueryController: catalog and metadata endpoints.
2. SatellitePassController: pass-related endpoints.
3. SatelliteCityController: city-based pass endpoints.
4. SatelliteCacheController: cache management endpoints.
2. Services
1. SatellitePassService: orchestration layer for pass computations.
2. PassTimeService: timezone/date conversion.
3. PassVisibilityService: sunlit/condition/visibility logic.
4. PassPhotometryService: estimated magnitude model.
5. TelegramNotificationService: notification workflow.

## Getting Started

### Prerequisites

1. Docker + Docker Compose
2. A free TCP 8080 port (or adjust compose mapping)

### Run

```bash
git clone <your-repo-url>
cd satelliteTracker
sudo docker compose up -d --build
```

### Stop

```bash
sudo docker compose down
```

### Stop And Remove Volumes

Use this only if you want to wipe DB and persisted data.

```bash
sudo docker compose down -v
```

## Configuration

Main runtime settings are in:

1. satelliteTracking/src/main/resources/application.properties
2. docker-compose.yml

Important environment variables:

1. SPRING_DATASOURCE_URL
2. SPRING_DATASOURCE_USERNAME
3. SPRING_DATASOURCE_PASSWORD
4. TELEGRAM_BOT_TOKEN

Recommended runtime secret handling:

1. Keep secrets in environment variables or secret stores, never in source files.
2. Do not commit `.env` files with real credentials.
3. Rotate tokens/passwords regularly.
4. Use different credentials for local, staging, and production environments.

Default observer location can be set via:

1. satellite.default-location.latitude
2. satellite.default-location.longitude
3. satellite.default-location.altitude
4. satellite.default-location.name

## Security And Data Exposure

This project should not expose sensitive data in API responses.

What is intentionally exposed:

1. Satellite metadata and computed visibility results.
2. Observer query parameters sent by the client.
3. Derived values such as azimuth, elevation, rise/set times, and estimated magnitude.

What must never be exposed:

1. Database credentials.
2. Telegram bot tokens.
3. Private infrastructure details or internal admin secrets.
4. Stack traces to public clients in production.

Hardening checklist:

1. Disable SQL debug logs in production (`spring.jpa.show-sql=false`).
2. Add centralized exception handling to sanitize error payloads.
3. Add authentication/authorization for administrative endpoints.
4. Restrict CORS to trusted frontend origins only.
5. Add rate limiting for public pass endpoints.

Note on this README:

1. All examples are intentionally non-sensitive.
2. No real tokens or private keys are included.

## API Reference

Base path:

`/api/satellites`

### Satellite Query Endpoints

1. `GET /api/satellites`
2. `GET /api/satellites/{id}`
3. `GET /api/satellites/norad/{noradCatId}`
4. `GET /api/satellites/{id}/latest-parameters`
5. `GET /api/satellites/{id}/orbital-history`
6. `GET /api/satellites/search-by-type?type=stations`
7. `GET /api/satellites/groups-stats`

### Pass Endpoints

1. `GET /api/satellites/{id}/passes?hours=24`
2. `GET /api/satellites/{id}/passes/custom?lat=41.9&lon=12.5&alt=20&hours=24`
3. `GET /api/satellites/observer-location`
4. `GET /api/satellites/upcoming-passes?hours=6`
5. `GET /api/satellites/upcoming-passes/custom?hours=6&minElevation=30&latitude=41.9&longitude=12.5&altitude=20`
6. `GET /api/satellites/upcoming-passes/filtered?hours=6&minElevation=30&observingCondition=night&maxMagnitude=6.0`
7. `GET /api/satellites/upcoming-passes/filtered/custom?...`
8. `GET /api/satellites/passes/upcoming?hours=3&latitude=41.01&longitude=14.3&altitude=30&minElevation=30`

### New Name-Based Pass Endpoints

1. `GET /api/satellites/passes/by-name?name=ISS&hours=24`
2. `GET /api/satellites/passes/iss?hours=24`

These endpoints allow querying passes without knowing a numeric satellite ID.

### City-Based Endpoints

1. `GET /api/satellites/{id}/passes/by-city?city=Rome&hours=24&minElevation=30`
2. `GET /api/satellites/upcoming-passes/by-city?city=Rome&hours=6&minElevation=30&observingCondition=any&maxMagnitude=6.0`

### Cache Endpoints

1. `GET /api/satellites/cache-status`
2. `DELETE /api/satellites/cache`

## Orbital Computation Flow

The pass pipeline is:

1. Load latest orbital parameters from database.
2. Build TLE lines from orbital parameters.
3. Initialize Orekit propagator (SGP4/SDP4 through TLE propagator).
4. Sweep time window with fixed step (currently 60 seconds).
5. Transform propagated position to topocentric frame of observer.
6. Detect rise/max/set events via elevation threshold crossing.
7. Compute sun geometry and classify sunlit state.
8. Determine observing condition (`night`, `twilight`, `daylight`).
9. Estimate apparent magnitude.
10. Apply filters (visibility, elevation, condition, magnitude).

Detailed execution stages:

1. Data acquisition and persistence
1. TLE-like orbital parameters are refreshed from external providers.
2. Latest parameters are selected per satellite before pass computation.
2. Geometric feasibility pre-filter
1. Satellites that cannot reach observer latitude (based on inclination) are skipped early.
2. This removes unnecessary propagation work.
3. Orbit propagation
1. Orekit propagates state vectors in ITRF frame over the requested time window.
2. Sample step is 60 seconds by default; this is a speed/precision trade-off.
4. Observer projection
1. Satellite position is projected into the observer topocentric frame.
2. Azimuth, elevation, and range are extracted for each step.
5. Pass event extraction
1. Rise starts when elevation crosses above horizon.
2. Maximum elevation is tracked while elevation remains positive.
3. Set is detected when elevation returns below horizon.
6. Illumination and visibility quality
1. Sun position is computed for each sampled epoch.
2. A hybrid sunlit model combines fast heuristics with refined shadow checks near borderline conditions.
3. Observation condition is derived from Sun elevation (`night`, `twilight`, `daylight`).
7. Photometry
1. Apparent magnitude is estimated from distance, phase angle, and sunlit state.
2. Values are clamped to a practical range for user-facing output.
8. Output filtering
1. Passes can be filtered by minimum elevation, condition, and max magnitude.
2. Results are sorted chronologically by rise time.
9. Caching
1. Upcoming-pass responses are cached by location and filter signature.
2. Cache entries are TTL-based and refreshed automatically.

Accuracy notes:

1. 60-second sampling means event times are approximate (not millisecond-accurate).
2. For higher precision, use smaller step sizes or interpolation at rise/set boundaries.
3. Fresh orbital data is critical; stale TLE data degrades prediction quality.

## Time Zones And Date Handling

1. Core orbital propagation is done in absolute time (UTC scale in Orekit).
2. Output timestamps are converted to local time based on observer coordinates via `PassTimeService`.
3. Global timezone lookup is implemented using the `timeshape` library.
4. If lookup fails, system timezone fallback is used.

Operational recommendation:

1. Keep container timezone deterministic (UTC is preferred).
2. Always convert for display using observer location, not server local clock.

## Testing

Current tests include:

1. Service unit tests
1. PassPhotometryServiceTest
2. PassVisibilityServiceTest
3. PassTimeServiceTest
2. Controller unit tests (Mockito)
1. SatellitePassControllerTest

Run tests locally:

```bash
cd satelliteTracking
./mvnw test
```

If your environment requires it:

```bash
export JAVA_HOME=<path-to-jdk-17>
```

## Troubleshooting

### Docker Build Fails On Maven Dependency Resolution

If a dependency version does not exist on Maven Central, update it in `satelliteTracking/pom.xml`.

### Build Fails During Test Compilation

`-DskipTests` skips execution, but test sources may still compile during package phase.
Ensure test dependencies and imports are compatible with your pom setup.

### Wrong Local Time In API Output

1. Ensure observer coordinates are passed correctly.
2. Verify timezone resolution in `PassTimeService`.
3. Check container/system timezone fallback behavior.

### Slow Upcoming Pass Calculations

1. Ensure DB index for latest-parameter pattern exists.
2. Use cache endpoints to inspect and clear stale cache.
3. Reduce search window (`hours`) and/or increase `minElevation` if needed.

### Unexpected Local Time In Results

1. Verify observer coordinates are correct.
2. Verify timezone lookup dependency is available at runtime.
3. Confirm server fallback timezone if coordinate lookup fails.

### Build Fails After Adding Tests

1. Ensure test classes compile with the dependencies declared in `pom.xml`.
2. If using slice tests (WebMvc), include matching Spring Boot test modules.
3. If build image is strict, prefer lightweight unit tests with Mockito for controller logic.

## Suggested Improvements

1. Add API authentication for non-public or costly endpoints.
2. Introduce rate limiting and request quotas per client.
3. Add structured logging and request correlation IDs.
4. Add actuator health/readiness endpoints for operations.
5. Add contract tests for public API payload stability.
6. Add interpolation-based rise/set refinement for sub-minute timing precision.
7. Add explicit timezone in API response metadata for client clarity.
8. Add benchmark tests for high-load upcoming-pass scenarios.
9. Add CI pipeline stages for lint, test, and Docker build.
10. Add OpenAPI/Swagger documentation generation.

## Development Notes

1. Keep controller classes focused by domain.
2. Keep orbital math logic in dedicated services.
3. Prefer unit tests for pure computation logic.
4. Add integration tests for critical API contracts.
5. Avoid mixing display timezone concerns with propagation logic.

## License

Rootking, contact me if interested

## Contributing

Pull requests are welcome. For large changes, open an issue first with:

1. Problem statement
2. Proposed approach
3. API/behavior impact
4. Test plan
