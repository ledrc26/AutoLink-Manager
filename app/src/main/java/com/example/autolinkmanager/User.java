package com.example.autolinkmanager;

public class User {
    private String uid;
    private String correo;
    private String nombre;

    public User() {} // Necesario para Firestore

    public User(String uid, String correo, String nombre) {
        this.uid = uid;
        this.correo = correo;
        this.nombre = nombre;
    }

    public String getUid() { return uid; }
    public String getCorreo() { return correo; }
    public String getNombre() { return nombre; }

    public void setUid(String uid) { this.uid = uid; }
    public void setCorreo(String correo) { this.correo = correo; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}

