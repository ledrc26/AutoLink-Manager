package com.example.autolinkmanager;

public class User {
    private String uid;
    private String correo;
    private String nombre;
    private String role;      // "admin" | "agency"
    private String agencyId;  // opcional (si pertenece a una agencia)

    public User() {}

    public User(String uid, String correo, String nombre, String role) {
        this.uid = uid;
        this.correo = correo;
        this.nombre = nombre;
        this.role = role;
        this.agencyId = null;
    }

    public String getUid() { return uid; }
    public String getCorreo() { return correo; }
    public String getNombre() { return nombre; }
    public String getRole() { return role; }
    public String getAgencyId() { return agencyId; }

    public void setUid(String uid) { this.uid = uid; }
    public void setCorreo(String correo) { this.correo = correo; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setRole(String rol) { this.role = rol; }
    public void setAgencyId(String agencyID) { this.agencyId = agencyID; }
}

