package com.example.autolinkmanager;

public class User {
    private String uid;
    private String correo;
    private String nombre;
    private String role;      // "admin" | "agency"
    private String agencyId;  // agencia a la que solicita/accede
    private boolean isActive;     // 0=pending, 1=activo

    public User() { }

    public User(String uid, String correo, String nombre, String role, String agencyId, boolean isActive) {
        this.uid = uid;
        this.correo = correo;
        this.nombre = nombre;
        this.role = role;
        this.agencyId = agencyId;
        this.isActive = isActive;
    }

    public String getUid() { return uid; }
    public String getCorreo() { return correo; }
    public String getNombre() { return nombre; }
    public String getRole() { return role; }
    public String getAgencyId() { return agencyId; }
    public boolean getIsActive() { return isActive; }

    public void setUid(String uid) { this.uid = uid; }
    public void setCorreo(String correo) { this.correo = correo; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setRole(String role) { this.role = role; }
    public void setAgencyId(String agencyId) { this.agencyId = agencyId; }
    public void setIsActive(boolean isActive) { this.isActive = isActive; }
}
