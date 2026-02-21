package com.satelliteTracking.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(nullable = false)
    private Long noradCatId;

    //Parametri orbitali del satellite

    private String epoch;
    private Double inclination;
    private Double raOfAscNode;
    private Double eccentricity;
    private Double argOfPericenter;
    private Double meanAnomaly;
    private Double meanMotion;


    public Satellite(){

    }

    public Satellite(Long id, String objectName, String objectId, Long noradCatId){

        this.id=id;
        this.objectName=objectName;
        this.objectId=objectId;
        this.noradCatId = noradCatId;
    }

}



