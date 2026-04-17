# 🛰️ Satellite Tracking

A production-ready backend for satellite tracking that predicts **when**, **where**, and **how well** satellites will be visible from any observer location.

It leverages up-to-date orbital data from CelesTrak, SGP4 propagation via Orekit, advanced visibility algorithms, and optional Telegram notifications.

---

## 🌍 Overview

Satellite Tracker answers questions like:

* **When will the ISS pass over my location?**
* **Which satellites will be visible in the next hours?**
* **Where should I look?** (azimuth / cardinal direction)
* **How good are the viewing conditions?** (night, twilight, daylight, illumination, estimated magnitude)

Designed for easy deployment with Docker Compose, it exposes a complete REST API.

---

## ✨ Key Features

* 📡 **Satellite Catalog** — Full catalog access with orbital history
* 🔮 **Pass Prediction** — Single or multi-satellite predictions with event-driven rise/peak/set detection
* 🌐 **Real-Time Position API** — Current geodetic position for one satellite or bulk map updates
* 📍 **Custom Observer Location** — Any coordinates supported
* 🏙️ **Geocoding** — City → coordinates conversion
* 👁️ **Visibility Classification** — Observation quality + hybrid sunlit/shadow logic
* ⚡ **Smart Caching** — Optimized performance for repeated queries and precomputed windows
* 📱 **Telegram Notifications** — Optional alert system
* 🚀 **ISS Shortcut** — Quick access without NORAD ID

---

## 🛠️ Tech Stack

```
Backend:    Java 17 + Spring Boot
Database:   PostgreSQL 15 + JPA/Hibernate
Orbit:      Orekit + Hipparchus
Deploy:     Docker + Docker Compose
Testing:    JUnit 5 + Mockito
```

---

## 🚀 Quick Start

### Prerequisites

* Docker & Docker Compose
* Port 8080 available

### Run

```bash
git clone <your-repo>
cd satelliteTracker
sudo docker compose up -d --build
```

Service will be available at:

```
http://localhost:8080
```

### Useful Commands

```bash
# Stop containers
sudo docker compose down

# Remove volumes (⚠️ data loss)
sudo docker compose down -v

# Logs
sudo docker compose logs -f

# Restart
sudo docker compose restart
```

### Dev/Prod Local or Remote Modes

Use the helper scripts under `scripts/` to switch quickly without editing `.env`:

```bash
# DEV only on localhost
./scripts/up-dev-local.sh

# DEV exposed on LAN/WAN (for remote tests)
./scripts/up-dev-remote.sh
./scripts/up-dev-remote.sh https
./scripts/up-dev-remote.sh http

# PROD only on localhost
./scripts/up-prod-local.sh

# PROD exposed on LAN/WAN (for reverse proxy)
./scripts/up-prod-remote.sh

# Stop profiles
./scripts/down-dev.sh
./scripts/down-prod.sh

# Single command switcher
./scripts/switch-mode.sh dev local
./scripts/switch-mode.sh dev remote
./scripts/switch-mode.sh dev remote https
./scripts/switch-mode.sh dev remote http
./scripts/switch-mode.sh prod local
./scripts/switch-mode.sh prod remote
./scripts/switch-mode.sh down

# Single down helper
./scripts/down.sh
```

Each `up-*` script automatically stops the opposite profile first to avoid port conflicts on `5173`.
For `dev remote`, choose protocol per use case:
- `https`: if your reverse proxy upstream is configured as HTTPS
- `http`: if your reverse proxy upstream is configured as HTTP
- no protocol argument: uses current `.env` value of `VITE_DEV_USE_HTTPS`

---

## ⚙️ Configuration

### Main Files

| File                     | Purpose              |
| ------------------------ | -------------------- |
| `application.properties` | Spring configuration |
| `docker-compose.yml`     | Containers setup     |

### Environment Variables

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/satellitedb
SPRING_DATASOURCE_USERNAME=your_user
SPRING_DATASOURCE_PASSWORD=your_password

TELEGRAM_BOT_TOKEN=your_token

# Bind app HTTP port (recommended: localhost when using a reverse proxy)
APP_BIND_ADDRESS=127.0.0.1
APP_PORT=8080
```

### Default Observer

```properties
satellite.default-location.latitude=<your_latitude>
satellite.default-location.longitude=<your_longitude>
satellite.default-location.altitude=30.0
satellite.default-location.name=<your_city>
```

### 🔐 Secrets Best Practices

* Use environment variables or secret managers
* Never commit credentials
* Rotate tokens regularly
* Separate environments (dev/staging/prod)

---

## 📡 API Overview

**Base URL:** `/api/satellites`

### Catalog Queries

```http
GET /api/satellites
GET /api/satellites/{id}
GET /api/satellites/norad/{id}
GET /api/satellites/{id}/latest-parameters
GET /api/satellites/{id}/orbital-history
GET /api/satellites/search-by-type?type=stations
GET /api/satellites/groups-stats
```

### Pass Predictions

```http
GET /api/satellites/{id}/passes?hours=24
GET /api/satellites/{id}/passes/custom?lat=...&lon=...&alt=...&hours=24
GET /api/satellites/upcoming-passes?hours=6
GET /api/satellites/upcoming-passes/custom?hours=6&minElevation=30&latitude=...&longitude=...&altitude=...
GET /api/satellites/upcoming-passes/filtered?hours=6&minElevation=30&observingCondition=any&maxMagnitude=6.0
GET /api/satellites/upcoming-passes/filtered/custom?hours=6&minElevation=30&observingCondition=any&maxMagnitude=6.0&latitude=...&longitude=...&altitude=...
GET /api/satellites/passes/upcoming?hours=3&latitude=...&longitude=...&altitude=...&minElevation=30
```

### Filters

* `minElevation`
* `observingCondition` (night, twilight, daylight)
* `maxMagnitude`

### Real-Time Positions

```http
GET /api/satellites/{id}/position
GET /api/satellites/positions
GET /api/satellites/positions?type=starlink
```

`/positions` is the recommended endpoint for frontend map polling.

### By Name / City

```http
GET /api/satellites/passes/by-name?name=ISS
GET /api/satellites/passes/iss
GET /api/satellites/{id}/passes/by-city?city=Rome&hours=24&minElevation=30
GET /api/satellites/upcoming-passes/by-city?city=Rome&hours=6&minElevation=30&observingCondition=any&maxMagnitude=6.0
```

### Cache

```http
GET /api/satellites/cache-status
DELETE /api/satellites/cache
```

---

## 🏗️ Architecture

### Layers

```
Controller → Service → Orbital Engine → Cache → Response
```

Controllers are split by use case:
* `SatelliteQueryController` for catalog/history queries
* `SatellitePassController` for visibility and pass prediction
* `SatellitePositionController` for current geodetic positions (single + bulk)
* `SatelliteCityController` for city-driven pass queries
* `SatelliteCacheController` for cache inspection/maintenance

### Core Services

* `SatellitePassService` — orchestration
* `PassTimeService` — UTC/local conversion and observer timezone resolution
* `PassVisibilityService` — visibility logic
* `PassPhotometryService` — magnitude estimation
* `TelegramNotificationService`

---

## 🔬 How It Works

### Processing Pipeline

1. Load orbital data (TLE)
2. Pre-filter satellites by inclination
3. Propagate orbit (SGP4 via Orekit)
4. Coarse scan to bracket candidate passes
5. Refine rise/set with `ElevationDetector`
6. Refine peak with `ElevationExtremumDetector` + slope filter
7. Re-sample refined timestamps in topocentric frame
8. Compute sun position, phase angle, and illumination
9. Classify observing conditions + estimate magnitude
10. Filter, sort, and cache response

### Accuracy Notes

* Coarse scan is 60s only for bracketing, not final output timestamps
* Rise/peak/set timestamps are refined by Orekit event detectors with millisecond-level numerical threshold
* Real-world accuracy still depends on TLE freshness and SGP4 model limits
* Numerical precision != physical prediction accuracy

---

## 🌐 Time Handling

* Core calculations: UTC
* Output: observer local timezone
* Automatic timezone resolution via coordinates

---

## 🧪 Testing

```bash
./mvnw test
```

Includes:

* Unit tests (services)
* Controller tests (Mockito)

---

## 🔧 Troubleshooting

### Slow Predictions

* Reduce `hours`
* Increase `minElevation`
* Check DB indexes
* Use cache

### Wrong Timezone

* Verify coordinates
* Check timezone resolver
* Validate container timezone

### Docker Build Issues

* Check Maven dependencies
* Ensure compatible versions

---

## 💡 Roadmap

### High Priority

* API authentication
* Rate limiting
* Health endpoints
* Structured logging

### Medium

* Optional detector tuning per satellite class (LEO/GEO)
* OpenAPI documentation
* Contract testing

### Low

* CI/CD pipeline
* Performance benchmarks

---

## 🤝 Contributing

PRs are welcome. For major changes:

1. Problem description
2. Proposed solution
3. API impact
4. Test plan

---

## 📄 License

Contact author for licensing details.

---

## 🙏 Credits

* Orekit — orbital mechanics
* CelesTrak — TLE data
* Timeshape — timezone resolution

---

<div align="center">

**Made with ☕ and 🛰️**

</div>
