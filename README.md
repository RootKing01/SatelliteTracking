# 🛰️ Satellite Tracker

Sistema completo per il tracciamento dei satelliti artificiali in orbita terrestre. Calcola quando e dove osservare i satelliti sopra la tua posizione, con dati in tempo reale da Celestrak e calcoli orbitali precisi tramite SGP4.

**Posizione predefinita:** San Marcellino, Caserta, Italia (41.01°N, 14.30°E)

---

## 📖 Indice

- [Cos'è questo progetto](#cosè-questo-progetto)
- [Concetti Base](#concetti-base)
- [Tecnologie](#tecnologie)
- [Installazione e Avvio](#installazione-e-avvio)
- [API Endpoints](#api-endpoints)
- [Come Funzionano i Calcoli](#come-funzionano-i-calcoli)
- [Interpretare i Risultati](#interpretare-i-risultati)
- [Esempi Pratici](#esempi-pratici)
- [Configurazione Avanzata](#configurazione-avanzata)
- [Troubleshooting](#troubleshooting)

---

## Cos'è questo progetto

Questo sistema ti permette di:

✅ **Tracciare satelliti** in tempo reale (ISS, Starlink, Hubble, etc.)  
✅ **Calcolare passaggi visibili** sopra qualsiasi posizione sulla Terra  
✅ **Sapere quando guardare** - orari precisi di rise, max elevation, set  
✅ **Sapere dove guardare** - direzione cardinale e gradi azimuth  
✅ **Valutare la visibilità** - elevazione, condizioni di illuminazione, magnitudine stimata  
✅ **Ricevere consigli** - suggerimenti testuali per l'osservazione  

I dati orbitali vengono aggiornati automaticamente ogni 6 ore da [Celestrak](https://celestrak.org/), garantendo precisione nei calcoli.

---

## Concetti Base

### 🧭 Azimuth (Azimut)

L'**azimuth** è l'angolo orizzontale misurato in **gradi** (da 0° a 360°) partendo dal **Nord** e procedendo in senso orario.

```
        N (0°/360°)
         |
         |
W (270°)---+---E (90°)
         |
         |
       S (180°)
```

**Esempi pratici:**
- Azimuth **0°** = Nord puro
- Azimuth **90°** = Est puro  
- Azimuth **180°** = Sud puro
- Azimuth **270°** = Ovest puro
- Azimuth **45°** = Nordest
- Azimuth **225°** = Sudovest

**Perché è importante?** Ti indica **verso quale direzione guardare sull'orizzonte** per vedere il satellite apparire (rise) o scomparire (set).

### 📐 Elevazione

L'**elevazione** è l'angolo verticale in **gradi** (da 0° a 90°) tra l'orizzonte e il satellite.

```
          90° (Zenit)
           * satellite
          /|
         / |
        /  | elevazione
       /   |
------/--------- 0° (Orizzonte)
```

**Esempi pratici:**
- Elevazione **0°** = Satellite esattamente sull'orizzonte
- Elevazione **45°** = Satellite a metà strada tra orizzonte e zenit
- Elevazione **90°** = Satellite direttamente sopra la testa

**Perché è importante?** Determina **quanto in alto guardare nel cielo**. Elevazioni > 30° sono ottime per l'osservazione.

### 🌍 Coordinate Geografiche

- **Latitudine**: da -90° (Polo Sud) a +90° (Polo Nord)
- **Longitudine**: da -180° (ovest) a +180° (est)  
- **Altitudine**: metri sopra il livello del mare

### 🎯 Magnitudine

Misura della **luminosità apparente** del satellite:
- **Valori negativi** = Molto luminosi (es. ISS a -3 come Venere)
- **Valori positivi bassi** (0-2) = Ben visibili
- **Valori alti** (>5) = Visibili solo con telescopio

### ☀️ Condizioni di Illuminazione

Un satellite è **visibile a occhio nudo** solo se:
1. È **illuminato dal Sole** (isSunlit = true)
2. Il **cielo è scuro** per l'osservatore (night o twilight)

**Momento migliore:** Subito dopo il tramonto o prima dell'alba, quando:
- Per te è buio (sole sotto l'orizzonte)
- Ma il satellite, più in alto, è ancora illuminato dal sole

---

## Tecnologie

### Backend
- **Spring Boot 4.0.3** - Framework Java per REST API
- **PostgreSQL 15** - Database per satelliti e parametri orbitali
- **Orekit 12.1** - Libreria per dinamica orbitale e propagazione SGP4/SDP4
- **Hipparchus 3.1** - Libreria matematica per calcoli astronomici
- **Spring WebFlux** - Client HTTP reattivo per chiamate a Celestrak
- **Hibernate/JPA** - ORM per persistenza dati
- **Lombok** - Riduzione boilerplate Java

### Infrastructure
- **Docker & Docker Compose** - Containerizzazione
- **Maven** - Build automation

### Algoritmi e Standard
- **SGP4/SDP4** - Simplified General Perturbations per propagazione orbitale
- **TLE** - Two-Line Element format per parametri orbitali
- **ITRF** - International Terrestrial Reference Frame
- **WGS84** - World Geodetic System per coordinate terrestri

---

## Installazione e Avvio

### Prerequisiti

- **Docker Desktop** installato e avviato
- **Git** (opzionale, per clonare il repository)
- **8080** porta libera (o modificare in docker-compose.yml)

### Avvio Rapido

```bash
# 1. Clona il repository (o scarica lo ZIP)
git clone <repository-url>
cd satelliteTracker

# 2. Avvia tutti i servizi con Docker Compose
docker compose up --build

# 3. Attendi che i servizi si avviino (2-3 minuti al primo avvio)
# Vedrai:
# ✅ Database PostgreSQL avviato
# ✅ Container dati Orekit creato
# ✅ Applicazione Spring Boot avviata sulla porta 8080
# ✅ Scheduler inizia a scaricare dati satelliti (primi 100 da Celestrak)
```

L'applicazione è pronta quando vedi:
```
satellite-app  | ✅ Orekit initialized with local data: /orekit-data
satellite-app  | Started SatelliteTrackerApplication in X.XXX seconds
```

### Verifica Funzionamento

```bash
# Controlla satelliti disponibili
curl http://localhost:8080/api/satellites

# Calcola passaggi ISS
curl http://localhost:8080/api/satellites/1/passes?hours=24
```

### Arresto

```bash
# Ferma i container mantenendo i dati
docker compose down

# Ferma e rimuovi anche i volumi (dati persi)
docker compose down -v
```

---

## API Endpoints

### 1. Lista Satelliti

```http
GET /api/satellites
```

Restituisce tutti i satelliti tracciati con i parametri orbitali più recenti.

**Risposta:**
```json
[
  {
    "id": 1,
    "noradCatId": 25544,
    "objectName": "ISS (ZARYA)",
    "objectType": "PAYLOAD",
    "orbitalParameters": [
      {
        "epoch": "2026-02-22T12:34:56",
        "inclination": 51.6416,
        "raan": 13.2515,
        "eccentricity": 0.0005678,
        "argOfPerigee": 123.4567,
        "meanAnomaly": 236.7890,
        "meanMotion": 15.501,
        "fetchedAt": "2026-02-22T18:00:00"
      }
    ]
  }
]
```

---

### 2. Calcola Passaggi (Posizione Predefinita)

```http
GET /api/satellites/{id}/passes?hours=24
```

**Parametri:**
- `id` (path, obbligatorio) - ID del satellite
- `hours` (query, default 24) - Ore nel futuro da analizzare

**Esempio:**
```bash
curl "http://localhost:8080/api/satellites/1/passes?hours=48"
```

**Risposta:**
```json
[
  {
    "satelliteId": 1,
    "satelliteName": "ISS (ZARYA)",
    "riseTime": "2026-02-22T22:45:30",
    "maxElevationTime": "2026-02-22T22:50:15",
    "setTime": "2026-02-22T22:55:45",
    "maxElevation": 68.5,
    "riseAzimuth": 238.5,
    "setAzimuth": 53.2,
    "maxDistance": 398.7,
    "isVisible": true,
    "isSunlit": true,
    "visibility": "excellent",
    "observingCondition": "night",
    "estimatedMagnitude": -2.5,
    "satelliteAltitudeKm": 418.3,
    "durationSeconds": 615,
    "riseDirection": "SW",
    "setDirection": "NE",
    "viewingTips": "Cerca il satellite verso SW (azimuth 238.5°). Passerà quasi sopra la tua testa! Ottima visibilità: satellite illuminato su cielo scuro."
  }
]
```

---

### 3. Calcola Passaggi (Posizione Personalizzata)

```http
GET /api/satellites/{id}/passes/custom?lat=41.9&lon=12.5&alt=20&hours=24
```

**Parametri:**
- `id` (path) - ID del satellite
- `lat` (query) - Latitudine (-90 a +90)
- `lon` (query) - Longitudine (-180 a +180)
- `alt` (query, default 0) - Altitudine in metri
- `hours` (query, default 24) - Ore da analizzare

**Esempio Roma:**
```bash
curl "http://localhost:8080/api/satellites/1/passes/custom?lat=41.9&lon=12.5&alt=20&hours=12"
```

---

### 4. Posizione Osservatore Predefinita

```http
GET /api/satellites/observer-location
```

**Risposta:**
```json
{
  "latitude": 41.01,
  "longitude": 14.30,
  "altitude": 30.0,
  "locationName": "San Marcellino, Caserta, Italia"
}
```

---

### 5. Dettagli Satellite

```http
GET /api/satellites/{id}
```

---

### 6. Storico Parametri Orbitali

```http
GET /api/satellites/{id}/orbital-history
```

Mostra tutti i TLE storici per vedere come l'orbita è cambiata nel tempo.

---

### 7. Cerca per NORAD ID

```http
GET /api/satellites/norad/{noradCatId}
```

**Esempio ISS:**
```bash
curl http://localhost:8080/api/satellites/norad/25544
```

---

## Come Funzionano i Calcoli

### 1. Acquisizione Dati (Celestrak)

Ogni 6 ore, lo scheduler scarica i TLE aggiornati da Celestrak:

```
https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=json
```

I dati includono:
- Identificatori (NORAD Catalog ID)
- Parametri orbitali (inclination, RAAN, eccentricity, etc.)
- Epoch (timestamp di validità)

### 2. Conversione TLE

I parametri orbitali vengono convertiti in formato **TLE standard** (Two-Line Elements):

```
ISS (ZARYA)
1 25544U 98067A   24054.12345678  .00000000  00000-0  00000-0 0    09
2 25544  51.6416  13.2515 0005678 123.4567 236.7890  15.50103472345678
```

- **Line 1**: Satellite ID, epoca, drag, radiazione
- **Line 2**: Inclinazione, RAAN, eccentricità, argomento perigeo, anomalia media, mean motion

### 3. Propagazione SGP4

**SGP4** (Simplified General Perturbations) è un algoritmo che:

1. Parte dai parametri TLE
2. Propaga l'orbita nel tempo considerando:
   - Attrazione gravitazionale terrestre (armoniche sferiche J2, J3, J4)
   - Resistenza atmosferica (drag)
   - Pressione di radiazione solare
   - Perturbazioni lunari e solari (per orbite alte)

3. Calcola posizione e velocità del satellite in ogni istante

**Precisione:** ~1 km per orbite LEO se il TLE è recente (<1 settimana)

### 4. Trasformazione Topocentric

Per calcolare cosa vede un osservatore sulla Terra:

```
Posizione Satellite (ITRF) 
    ↓
Trasformazione → Frame Topocentrico (osservatore)
    ↓
Estrazione → Azimuth, Elevazione, Distanza
```

**Processo:**
- Ogni 60 secondi, calcola posizione satellite
- Trasforma in coordinate relative all'osservatore
- Estrae azimuth (0-360°) ed elevazione (-90 a +90°)
- Calcola distanza slant-range (km)

### 5. Rilevamento Passaggi

Un **passaggio** è identificato quando:

1. **Rise**: Satellite supera l'orizzonte (elevazione passa da <0° a >0°)
2. **Max Elevation**: Punto di massima elevazione durante il passaggio
3. **Set**: Satellite scende sotto l'orizzonte (elevazione torna <0°)

**Filtro visibilità minima:** Solo passaggi con elevazione massima >10° (altrimenti troppo bassi).

### 6. Calcolo Illuminazione

Per determinare se il satellite è visibile:

1. **Posizione Sole**: Calcolata con effemeridi preciseDatella libreria Orekit
2. **Angolo Satellite-Sole**: Se <90°, satellite è illuminato
3. **Elevazione Sole per Osservatore**:
   - Sole < -18° → Notte astronomica
   - Sole tra -18° e -6° → Crepuscolo
   - Sole > -6° → Giorno

**Visibilità ottimale:** Satellite illuminato + Osservatore al buio

### 7. Stima Magnitudine

Formula semplificata basata su:
- Distanza dal satellite (più vicino = più luminoso)
- Altitudine orbitale (LEO più luminose di MEO/GEO)
- Stato di illuminazione

ISS tipicamente varia da **-3** (brillantissima) a **+2** (ben visibile).

---

## Interpretare i Risultati

### 📊 Campi della Risposta

| Campo | Significato | Valori |
|-------|-------------|--------|
| `riseTime` | Quando il satellite appare sull'orizzonte | ISO 8601 timestamp |
| `maxElevationTime` | Momento di massima altezza | ISO 8601 timestamp |
| `setTime` | Quando scompare sotto l'orizzonte | ISO 8601 timestamp |
| `maxElevation` | Altezza massima sopra orizzonte | 0-90° |
| `riseAzimuth` | Direzione dove appare | 0-360° |
| `setAzimuth` | Direzione dove scompare | 0-360° |
| `maxDistance` | Distanza nel punto più vicino | km |
| `isVisible` | Effettivamente visibile a occhio nudo | true/false |
| `isSunlit` | Satellite illuminato dal sole | true/false |
| `visibility` | Qualità dell'osservazione | excellent/good/fair/poor |
| `observingCondition` | Condizioni di luce | night/twilight/daylight |
| `estimatedMagnitude` | Luminosità apparente | -4 a +6 |
| `satelliteAltitudeKm` | Altitudine orbitale | km |
| `durationSeconds` | Durata totale passaggio | secondi |
| `riseDirection` | Direzione cardinale rise | N/NE/E/SE/S/SW/W/NW |
| `setDirection` | Direzione cardinale set | N/NE/E/SE/S/SW/W/NW |
| `viewingTips` | Suggerimenti testuali | stringa |

### 🎯 Quali Passaggi Osservare

**Migliori condizioni:**
- ✅ `visibility: "excellent"` o `"good"`
- ✅ `isVisible: true`
- ✅ `observingCondition: "night"`
- ✅ `maxElevation > 40°`
- ✅ `estimatedMagnitude < 3`

**Condizioni discrete:**
- ⚠️ `visibility: "fair"`
- ⚠️ `observingCondition: "twilight"`
- ⚠️ `maxElevation 20-40°`

**Da evitare:**
- ❌ `visibility: "poor"`
- ❌ `observingCondition: "daylight"`
- ❌ `isVisible: false`
- ❌ `maxElevation < 20°`

---

## Esempi Pratici

### Esempio 1: Osservare la ISS Stasera

```bash
# 1. Trova l'ID della ISS
curl http://localhost:8080/api/satellites | jq '.[] | select(.objectName | contains("ISS"))'

# 2. Calcola passaggi prossime 12 ore
curl "http://localhost:8080/api/satellites/1/passes?hours=12" | jq

# 3. Filtra solo passaggi ottimi (elevazione > 40°)
curl "http://localhost:8080/api/satellites/1/passes?hours=12" | jq '.[] | select(.maxElevation > 40)'

# 4. Ordina per magnitudine (più luminosi)
curl "http://localhost:8080/api/satellites/1/passes?hours=12" | jq 'sort_by(.estimatedMagnitude)'
```

### Esempio 2: Migliore Passaggio ISS nei Prossimi 3 Giorni

```bash
curl "http://localhost:8080/api/satellites/1/passes?hours=72" | \
  jq '[.[] | select(.visibility == "excellent")] | sort_by(.maxElevation) | reverse | .[0]'
```

Risultato: Il passaggio con elevazione più alta nei prossimi 3 giorni.

### Esempio 3: Tutti i Satelliti Visibili Oggi

```bash
# Per ogni satellite, calcola passaggi e conta quanti sono visibili
for id in $(curl -s http://localhost:8080/api/satellites | jq '.[].id'); do
  name=$(curl -s http://localhost:8080/api/satellites/$id | jq -r '.objectName')
  visible=$(curl -s "http://localhost:8080/api/satellites/$id/passes?hours=24" | jq '[.[] | select(.isVisible == true)] | length')
  echo "$name: $visible passaggi visibili"
done
```

### Esempio 4: Passaggio ISS su Roma

```bash
curl "http://localhost:8080/api/satellites/1/passes/custom?lat=41.9&lon=12.5&alt=20&hours=24" | jq
```

### Esempio 5: Quando Osservare Starlink

```bash
# Trova Starlink disponibili
curl http://localhost:8080/api/satellites | jq '.[] | select(.objectName | contains("STARLINK")) | {id, name: .objectName}'

# Calcola passaggi per uno specifico
curl "http://localhost:8080/api/satellites/42/passes?hours=24" | jq '.[] | select(.isVisible == true)'
```

---

## Configurazione Avanzata

### Cambiare Posizione Predefinita

Modifica `ObserverLocation.java`:

```java
public static ObserverLocation sanMarcellino() {
    return new ObserverLocation(
        45.46,  // Latitudine Milano
        9.19,   // Longitudine Milano
        120.0,  // Altitudine
        "Milano, Italia"
    );
}
```

### Modificare Frequenza Aggiornamenti

In `SatelliteScheduler.java`:

```java
@Scheduled(fixedRate = 21600000) // 6 ore in millisecondi
// Cambia in 3600000 per 1 ora, 43200000 per 12 ore, etc.
```

### Cambiare Elevazione Minima

In `SatellitePassService.java`:

```java
if (pd.maxElevation > 10.0) {  // Cambia 10.0 in altro valore
```

### Modificare Step di Calcolo

In `SatellitePassService.java`:

```java
double step = 60.0; // Secondi tra ogni calcolo
// 30.0 = più preciso ma più lento
// 120.0 = meno preciso ma più veloce
```

### Aggiungere Altri Gruppi Satelliti

In `CelestrakService.java`:

```java
private static final String[] SATELLITE_GROUPS = {
    "stations",      // ISS, Tiangong
    "starlink",      // Tutti i Starlink
    "planet",        // Planet Labs
    "science",       // Hubble, JWST
    "weather",       // NOAA, GOES
    "amateur"        // Satelliti radioamatoriali
};
```

---

## Troubleshooting

### ❌ Errore: "Failed to initialize Orekit"

**Causa:** Dati astronomici Orekit non scaricati correttamente.

**Soluzione:**
```bash
docker compose down -v
docker compose up --build --force-recreate
```

### ❌ Nessun Satellite nel Database

**Causa:** Scheduler non è partito o Celestrak non raggiungibile.

**Soluzione:**
```bash
# Verifica log
docker logs satellite-app

# Forza il download manualmente
curl -X POST http://localhost:8080/api/satellites/refresh
```

### ❌ Nessun Passaggio Trovato

**Possibili cause:**
1. Satellite con inclinazione insufficiente per la tua latitudine
2. Periodo di analisi troppo breve
3. Parametri orbitali obsoleti

**Soluzioni:**
```bash
# Aumenta periodo
curl "http://localhost:8080/api/satellites/1/passes?hours=72"

# Verifica ultimo aggiornamento parametri
curl http://localhost:8080/api/satellites/1 | jq '.orbitalParameters[0].fetchedAt'

# Prova satellite diverso (ISS quasi sempre visibile)
curl "http://localhost:8080/api/satellites/1/passes?hours=24"
```

### ❌ Passaggi Non Accurati

**Causa:** TLE vecchi (perdono precisione dopo giorni/settimane).

**Soluzione:**
- Assicurati che lo scheduler stia aggiornando (ogni 6 ore)
- Verifica `fetchedAt` recente
- Celestrak potrebbe essere temporaneamente down

### ❌ Porta 8080 Occupata

Modifica `docker-compose.yml`:

```yaml
ports:
  - "9090:8080"  # Usa porta 9090 esterna
```

Poi accedi su `http://localhost:9090`

### ❌ Build Lento

**Prima build:** Normale (scarica Orekit data ~50MB).

**Build successivi:** Usa cache Docker. Se sempre lento:
```bash
# Pulisci cache Maven
docker compose down
docker volume prune
docker compose up --build
```

---

## 📚 Risorse Utili

### Documentazione Tecnica
- [Orekit Documentation](https://www.orekit.org/)
- [SGP4 Model Explained](https://en.wikipedia.org/wiki/Simplified_perturbations_models)
- [TLE Format Specification](https://en.wikipedia.org/wiki/Two-line_element_set)
- [Celestrak](https://celestrak.org/) - Fonte dati TLE

### Astronomia e Satelliti
- [Heavens Above](https://www.heavens-above.com/) - Tracking satelliti online
- [N2YO](https://www.n2yo.com/) - Real-time satellite tracking
- [ISS Tracker](https://spotthestation.nasa.gov/) - NASA ISS notifications

### Coordinate e Mappe
- [LatLong.net](https://www.latlong.net/) - Trova coordinate di qualsiasi luogo
- [Google Maps](https://maps.google.com) - Click destro → coordinate

---

## 🎉 Funzionalità Implementate

✅ Tracciamento satelliti con dati TLE da Celestrak  
✅ Aggiornamento automatico ogni 6 ore  
✅ Calcoli orbitali precisi con SGP4 e Orekit  
✅ Calcolo passaggi visibili per qualsiasi posizione  
✅ Azimuth, elevazione, distanza, durata  
✅ Direzioni cardinali (N, NE, E, SE, S, SW, W, NW)  
✅ Calcolo illuminazione satellite (sunlit)  
✅ Condizioni di osservazione (night/twilight/daylight)  
✅ Stima magnitudine apparente  
✅ Qualità visibilità (excellent/good/fair/poor)  
✅ Suggerimenti testuali per l'osservazione  
✅ Storico parametri orbitali  
✅ API REST completa  
✅ Containerizzazione Docker completa  
✅ Separazione dati Orekit in container dedicato  
✅ Nessuna duplicazione satelliti nel database  

---

## 📝 Licenza

Progetto educational per tracking satelliti.

---

## 👨‍💻 Sviluppo

**Stack:**
- Java 21
- Spring Boot 4.0.3
- PostgreSQL 15
- Orekit 12.1
- Docker & Docker Compose

**Struttura Progetto:**
```
satelliteTracker/
├── docker-compose.yml           # Orchestrazione container
├── orekit-data.Dockerfile      # Container dati Orekit
├── satelliteTracking/
│   ├── Dockerfile              # Container applicazione
│   ├── pom.xml                 # Dipendenze Maven
│   └── src/main/java/com/satelliteTracking/
│       ├── config/
│       │   └── OrekitConfig.java
│       ├── controller/
│       │   └── SatelliteController.java
│       ├── dto/
│       │   ├── SatellitePassDTO.java
│       │   └── CelestrakSatelliteDTO.java
│       ├── model/
│       │   ├── Satellite.java
│       │   ├── OrbitalParameters.java
│       │   └── ObserverLocation.java
│       ├── repository/
│       │   ├── SatelliteRepository.java
│       │   └── OrbitalParametersRepository.java
│       ├── scheduler/
│       │   └── SatelliteScheduler.java
│       ├── service/
│       │   ├── CelestrakService.java
│       │   └── SatellitePassService.java
│       └── util/
│           └── TLEConverter.java
```

---

**Buon tracking! 🛰️✨**

*Data: 22 Febbraio 2026*  
*Versione: 2.0.0*
