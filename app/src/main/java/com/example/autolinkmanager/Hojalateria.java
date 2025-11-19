package com.example.autolinkmanager;

import com.google.firebase.firestore.PropertyName;

import java.util.Date;
import java.util.List;

public class Hojalateria extends Auto{
    private List<String> tiposTrabajo; // Stores selected chip texts (e.g., ["Pintar el carro", "Pulido y encerado"])
    private String colorHex; // Stores the color hex string (e.g., "44CAF50")
    private Date fechaIngreso;
    private Date fechaSalida;
    private double costo;
    private boolean pagado; // true for "Pagado", false for "No pagado"
    private String notas;
    private boolean isFinished;      // Nuevo campo estado
    private String fotoTerminadoBase64;

    // --- Constructor ---

    public Hojalateria() {
        super(); // Calls the empty constructor of Auto
    }

    // --- Getters and Setters for Hojalateria fields ---

    public List<String> getTiposTrabajo() {
        return tiposTrabajo;
    }

    public void setTiposTrabajo(List<String> tiposTrabajo) {
        this.tiposTrabajo = tiposTrabajo;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public Date getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(Date fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public Date getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(Date fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public double getCosto() {
        return costo;
    }

    public void setCosto(double costo) {
        this.costo = costo;
    }

    public boolean isPagado() { // Standard getter name for boolean
        return pagado;
    }

    public void setPagado(boolean pagado) {
        this.pagado = pagado;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }
    @PropertyName("isFinished")
    public boolean isFinished() {
        return isFinished;
    }
    @PropertyName("isFinished")
    public void setFinished(boolean finished) {
        isFinished = finished;
    }

    public String getFotoTerminadoBase64() {
        return fotoTerminadoBase64;
    }

    public void setFotoTerminadoBase64(String fotoTerminadoBase64) {
        this.fotoTerminadoBase64 = fotoTerminadoBase64;
    }
}
