package com.satelliteTracking.config;

import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Configurazione per la libreria Orekit
 * Orekit richiede dati astronomici per calcoli precisi
 */
@Configuration
public class OrekitConfig {

    private volatile boolean orekitDataLoaded;
    private volatile String orekitDataPath = "/orekit-data";

    @PostConstruct
    public void initOrekit() {
        try {
            DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
            
            // Prova prima a usare una directory locale se esiste
            File orekitData = new File(orekitDataPath);
            if (orekitData.exists() && orekitData.isDirectory()) {
                manager.addProvider(new DirectoryCrawler(orekitData));
                orekitDataLoaded = true;
                System.out.println("✅ Orekit initialized with local data: " + orekitData.getAbsolutePath());
            } else {
                orekitDataLoaded = false;
                // Senza dati Orekit, usa calcoli semplificati
                System.out.println("⚠️  Orekit data not found - using simplified calculations");
                System.out.println("ℹ️  For precise calculations, mount orekit-data in /orekit-data");
                System.out.println("ℹ️  Download from: https://gitlab.orekit.org/orekit/orekit-data");
            }
            
        } catch (Exception e) {
            orekitDataLoaded = false;
            System.err.println("⚠️  Orekit initialization warning: " + e.getMessage());
            System.err.println("ℹ️  Satellite pass calculations will use simplified model");
        }
    }

    public boolean isOrekitDataLoaded() {
        return orekitDataLoaded;
    }

    public String getOrekitDataPath() {
        return orekitDataPath;
    }

    /**
     * Bean per RestTemplate - utilizzato da TelegramNotificationService
     * per effettuare chiamate HTTP all'API di Telegram
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
