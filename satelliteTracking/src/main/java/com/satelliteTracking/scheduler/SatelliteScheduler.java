package com.satelliteTracking.scheduler;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.service.SpaceMissionService;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.TelegramNotificationService;
import com.satelliteTracking.service.TleDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(value = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SatelliteScheduler {

    // Intervalli di aggiornamento TLE
    private static final long FULL_UPDATE_HOURS = 12;    // Aggiornamento completo ogni 12 ore
    private static final long LEO_UPDATE_HOURS = 3;      // Aggiornamento LEO ogni 3 ore

    private final TleDataService tleDataService;
    private final SpaceMissionService spaceMissionService;
    private final SatellitePassService passService;
    private final TelegramNotificationService telegramNotificationService;
    private final OrbitalParametersRepository orbitalParametersRepository;

    public SatelliteScheduler(TleDataService tleDataService,
                              SpaceMissionService spaceMissionService,
                              SatellitePassService passService,
                              TelegramNotificationService telegramNotificationService,
                              OrbitalParametersRepository orbitalParametersRepository) {
        this.tleDataService = tleDataService;
        this.spaceMissionService = spaceMissionService;
        this.passService = passService;
        this.telegramNotificationService = telegramNotificationService;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    /**
     * 🚀 Avvio immediato dopo startup
     */

    /**
     * 🔄 UPDATE TLE COMPLETO ogni 12 ore
     * Aggiorna tutti i satelliti (LEO + MEO + GEO + HEO)
     */
    @Scheduled(fixedRate = 43200000, initialDelay = 60000) // 12 ore
    public void updateSatellitesFull() {

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🔄 [Full Update] Verifica aggiornamento TLE completo...");
        System.out.println("═══════════════════════════════════════════════════════════");

        OrbitalParameters lastUpdate = orbitalParametersRepository
            .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);

        if (lastUpdate != null) {
            
            long minutes = Duration.between(lastUpdate.getFetchedAt(), LocalDateTime.now()).toMinutes();


            if (minutes < 30) {
                System.out.println("⏭️ Skip update: ultimo fetch troppo recente (" + minutes + " min fa)");
                System.out.println("ℹ️ Nessun fetch necessario: i dati sono già aggiornati.");

                return;
            }

            long hours = minutes / 60;

            if (hours < FULL_UPDATE_HOURS) {
                System.out.println("⏭️ Skip full update (ultimo aggiornamento " + hours + "h fa)");
                System.out.println("   Prossimo update completo tra: " + (FULL_UPDATE_HOURS - hours) + "h");
                System.out.println("═══════════════════════════════════════════════════════════");
                return;
            }

            System.out.println("✅ Update completo necessario (" + hours + "h dall'ultimo)");
        } else {
            System.out.println("✅ Primo download TLE (database vuoto)");
        }

        try {
            tleDataService.updateTle();

            passService.clearPassesCache();
            precomputeUpcomingPasses();

            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("✅ [Full Update] Aggiornamento completo terminato");
            System.out.println("═══════════════════════════════════════════════════════════");

        } catch (Exception e) {
            System.err.println("❌ [Full Update] Errore: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 🛰️ UPDATE TLE LEO ogni 3 ore
     * Aggiorna solo i satelliti in orbita bassa (più soggetti a variazioni)
     * 
     * Schedulazione: ogni 3 ore (10800000 ms)
     * Initial delay: 10 minuti per non sovrapporsi al full update iniziale
     */
    @Scheduled(fixedRate = 10800000, initialDelay = 600000) // 3 ore, delay 10min
    public void updateSatellitesLeoOnly() {

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🛰️  [LEO Update] Verifica aggiornamento TLE LEO...");
        System.out.println("═══════════════════════════════════════════════════════════");

        OrbitalParameters lastUpdate = orbitalParametersRepository
            .findTopBySatellite_NoradCatIdGreaterThanOrderByFetchedAtDesc(0L);

        if (lastUpdate == null) {
            System.out.println("⏭️ Skip LEO update: database non inizializzato");
            System.out.println("   Attendo primo full update...");
            System.out.println("═══════════════════════════════════════════════════════════");
            return;
        }

        long hours = Duration.between(lastUpdate.getFetchedAt(), LocalDateTime.now()).toHours();

        // Se è passato meno del tempo LEO, skip
        if (hours < LEO_UPDATE_HOURS) {
            System.out.println("⏭️ Skip LEO update (ultimo aggiornamento " + hours + "h fa)");
            System.out.println("   Prossimo LEO update tra: " + (LEO_UPDATE_HOURS - hours) + "h");
            System.out.println("═══════════════════════════════════════════════════════════");
            return;
        }

        // Se è quasi tempo per un full update (entro 1h), skip il LEO
        if (hours >= FULL_UPDATE_HOURS - 1) {
            System.out.println("⏭️ Skip LEO update: full update imminente");
            System.out.println("   Full update tra: " + (FULL_UPDATE_HOURS - hours) + "h");
            System.out.println("═══════════════════════════════════════════════════════════");
            return;
        }

        System.out.println("✅ Update LEO necessario (" + hours + "h dall'ultimo)");

        try {
            tleDataService.updateTleLeoOnly();

            // Invalida solo la cache dei passaggi (i LEO cambiano rapidamente)
            passService.clearPassesCache();
            precomputeUpcomingPasses();

            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("✅ [LEO Update] Aggiornamento LEO terminato");
            System.out.println("═══════════════════════════════════════════════════════════");

        } catch (Exception e) {
            System.err.println("❌ [LEO Update] Errore: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 🚀 Sincronizzazione missioni spaziali ogni 3 ore
     * Job separato dai TLE Space-Track così non altera il timing LEO.
     */
    @Scheduled(fixedDelay = 3 * 60 * 60 * 1000, initialDelay = 600000)
    public void syncSpaceMissions() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🚀 [Mission Sync] Verifica sincronizzazione missioni...");
        System.out.println("═══════════════════════════════════════════════════════════");

        try {
            spaceMissionService.syncSpaceMissions();

            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("✅ [Mission Sync] Sincronizzazione missioni terminata");
            System.out.println("═══════════════════════════════════════════════════════════");
        } catch (Exception e) {
            System.err.println("❌ [Mission Sync] Errore: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 📩 Poll Telegram ogni 10 secondi
     */
    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void pollTelegramMessages() {
        try {
            telegramNotificationService.pollTelegramUpdates();
        } catch (Exception ignored) {}
    }

    /**
     * 🧠 Precalcolo passaggi ogni ora
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 120000)
    public void precomputeUpcomingPasses() {
        System.out.println("🔄 [Pass Precalculator] Pre-calcolo passaggi...");

        try {
            List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(3, 10.0);
            System.out.println("✅ Default cache: " + passes.size());

            List<TelegramSubscription> subs = telegramNotificationService.getAllSubscriptions();

            if (!subs.isEmpty()) {
                passService.precomputePassesForSubscriptions(subs);
                System.out.println("✅ Cache utenti aggiornata: " + subs.size());
            }

        } catch (Exception e) {
            System.err.println("❌ Errore pre-calcolo: " + e.getMessage());
        }
    }

    /**
     * 📢 Notifiche Telegram ogni ora
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 180000)
    public void sendTelegramNotificationsForUpcomingPasses() {

        System.out.println("📢 [Telegram Scheduler] Scan passaggi...");

        try {
            List<TelegramSubscription> subs = telegramNotificationService.getAllSubscriptions();

            for (TelegramSubscription sub : subs) {

                if (!sub.getNotificationsEnabled()) continue;

                try {
                    ObserverLocation location = new ObserverLocation(
                            sub.getLatitude(),
                            sub.getLongitude(),
                            sub.getAltitude(),
                            sub.getLocationName()
                    );

                    List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(
                            3,
                            sub.getMinElevation(),
                            location,
                            sub.getObservingCondition(),
                            sub.getMaxMagnitude()
                    );

                    if (sub.getLastNotificationSent() != null) {
                        long minutes = Duration.between(sub.getLastNotificationSent(), LocalDateTime.now()).toMinutes();
                        if (minutes < 30) continue;
                    }

                    final int maxAuto = 10;
                    List<SatellitePassDTO> autoPasses = passes.stream().limit(maxAuto).toList();

                    for (SatellitePassDTO pass : autoPasses) {
                        telegramNotificationService.sendNotificationToUser(sub, pass);
                    }

                    if (passes.size() > maxAuto) {
                        telegramNotificationService.sendSystemMessage(
                                sub.getChatId(),
                                "ℹ️ Inviati i primi " + maxAuto + " su " + passes.size() +
                                        " passaggi. Usa /allpasses per il resto."
                        );
                    }

                } catch (Exception e) {
                    System.err.println("⚠️ Errore sub " + sub.getId() + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Telegram scheduler error: " + e.getMessage());
        }
    }
}