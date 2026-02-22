package com.satelliteTracking.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name="satellites")
public class Satellite{

    //Dati di identificazione del satellite

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String objectName;

    @Column(nullable = false)
    private String objectId;

    @Column(nullable = false, unique = true)
    private Long noradCatId;

    //Relazione con i parametri orbitali (storico)
    @OneToMany(mappedBy = "satellite", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrbitalParameters> orbitalParametersList = new ArrayList<>();

    public Satellite(){

    }

    public Satellite(Long id, String objectName, String objectId, Long noradCatId){
        this.id=id;
        this.objectName=objectName;
        this.objectId=objectId;
        this.noradCatId = noradCatId;
    }

    // Metodo helper per aggiungere parametri orbitali
    public void addOrbitalParameters(OrbitalParameters parameters) {
        orbitalParametersList.add(parameters);
        parameters.setSatellite(this);
    }
}



