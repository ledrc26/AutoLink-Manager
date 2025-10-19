package com.example.autolinkmanager;

import java.util.Date; // Necesitarás importar la clase Date

public class Mantenimiento extends Auto { // Ya es Serializable por herencia

    private String tipoMantenimiento;
    private Date fechaIngreso;
    private Date fechaSalida;
    private double costo;
    private boolean pagado;
    private String notas;

    public Mantenimiento() {
        super(); // Llama al constructor vacío de Auto
    }

    // --- Getters y Setters para Mantenimiento ---

    public String getTipoMantenimiento() { return tipoMantenimiento; }
    public void setTipoMantenimiento(String tipoMantenimiento) { this.tipoMantenimiento = tipoMantenimiento; }

    public Date getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(Date fechaIngreso) { this.fechaIngreso = fechaIngreso; }

    public Date getFechaSalida() { return fechaSalida; }
    public void setFechaSalida(Date fechaSalida) { this.fechaSalida = fechaSalida; }

    public double getCosto() { return costo; }
    public void setCosto(double costo) { this.costo = costo; }

    public boolean isPagado() { return pagado; }
    public void setPagado(boolean pagado) { this.pagado = pagado; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
