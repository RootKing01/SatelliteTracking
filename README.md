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
```

### Default Observer

```properties
satellite.default-location.latitude=41.9
satellite.default-location.longitude=12.5
satellite.default-location.altitude=20
satellite.default-location.name=Rome
```

### 🔐 Secrets Best Practices

* Use environment variables or secret managers
* Never commit credentials
* Rotate tokens regularly
* Separate environments (dev/staging/prod)

---

## 📡 API Overview

**Base URL:** `/api/satellites`

### Satellite Queries

```http
GET /api/satellites
GET /api/satellites/{id}
GET /api/satellites/norad/{id}
GET /api/satellites/search-by-type?type=stations
```

### Pass Predictions

```http
GET /api/satellites/{id}/passes?hours=24
GET /api/satellites/{id}/passes/custom?...
GET /api/satellites/upcoming-passes?hours=6
```

### Filters

* `minElevation`
* `observingCondition` (night, twilight, daylight)
* `maxMagnitude`

### By Name / City

```http
GET /api/satellites/passes/by-name?name=ISS
GET /api/satellites/passes/iss
GET /api/satellites/.../by-city?city=Rome
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
