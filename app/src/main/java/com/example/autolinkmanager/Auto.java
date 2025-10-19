package com.example.autolinkmanager;

import java.io.Serializable;

public class Auto implements Serializable {

    private String agencyId; // <-- CAMPO AÑADIDO
    private String placa;
    private String modelo;
    private int anio;
    private String nombrePropietario;
    private String telefonoPropietario;
    private String fotoBase64;

    public Auto() {
    }

    // --- Getters y Setters ---

    public String getAgencyId() { return agencyId; } // <-- Getter añadido
    public void setAgencyId(String agencyId) { this.agencyId = agencyId; } // <-- Setter añadido

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public String getNombrePropietario() { return nombrePropietario; }
    public void setNombrePropietario(String nombrePropietario) { this.nombrePropietario = nombrePropietario; }

    public String getTelefonoPropietario() { return telefonoPropietario; }
    public void setTelefonoPropietario(String telefonoPropietario) { this.telefonoPropietario = telefonoPropietario; }

    public String getFotoBase64() { return fotoBase64; }
    public void setFotoBase64(String fotoBase64) { this.fotoBase64 = fotoBase64; }
}