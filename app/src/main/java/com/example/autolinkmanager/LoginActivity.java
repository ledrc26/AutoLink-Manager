package com.example.autolinkmanager;


import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.autolinkmanager.MainActivity;
import com.example.autolinkmanager.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoRegister;
    private ProgressBar progress;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db; // <--- AÑADIR ESTO

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Primero, inflamos la vista SIEMPRE
        setContentView(R.layout.activity_login);

        // Inicializamos las vistas
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoRegister = findViewById(R.id.tvGoRegister);
        progress = findViewById(R.id.progress);

        // Asignamos los listeners
        btnLogin.setOnClickListener(v -> doLogin());
        tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        // AHORA SÍ, verificamos el auto-login
        if (mAuth.getCurrentUser() != null) {
            // Usuario ya logueado, pero debemos verificar su estado
            progress.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false); // Bloquear UI mientras verificamos
            tvGoRegister.setEnabled(false);

            checkUserStatus(); // Reutilizamos nuestro método
        }
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) { etEmail.setError("Requerido"); return; }
        if (TextUtils.isEmpty(pass))  { etPassword.setError("Requerido"); return; }

        progress.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // ¡Éxito en Auth! PERO ESPERA...
                        // Ahora verificamos el estado en Firestore
                        checkUserStatus(); // <--- CAMBIO AQUÍ
                    } else {
                        // Error de Auth (email, contraseña incorrecta, etc.)
                        progress.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        String msg = task.getException() != null ? task.getException().getMessage() : "Error de autenticación";
                        Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Nuevo método: Verifica el documento del usuario en Firestore
     * para ver si existe y si está activo.
     */
    private void checkUserStatus() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // Esto no debería pasar si llegamos aquí, pero es buena idea verificar
            progress.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
            return;
        }

        String uid = user.getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    // 1. Verificar si el documento EXISTE
                    if (documentSnapshot.exists()) {

                        // 2. Verificar el campo "isActive" (asumiendo que es booleano)
                        Boolean isActive = documentSnapshot.getBoolean("isActive");

                        // Boolean.TRUE.equals() previene NullPointerException si el campo no existe
                        if (Boolean.TRUE.equals(isActive)) {
                            // ¡ÉXITO! Usuario existe Y está activo
                            goToMain();
                        } else {
                            // Usuario existe, pero isActive es false o nulo
                            progress.setVisibility(View.GONE);
                            btnLogin.setEnabled(true);
                            toast("Tu cuenta no está activa. Contacta al administrador.");
                            mAuth.signOut(); // Desloguear de Auth
                        }
                    } else {
                        // Usuario NO existe en Firestore (aunque sí en Auth)
                        progress.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        toast("Tu usuario no tiene datos registrados. Contacta al administrador.");
                        mAuth.signOut(); // Desloguear de Auth
                    }
                })
                .addOnFailureListener(e -> {
                    // Error al leer Firestore (sin internet, permisos, etc.)
                    progress.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    toast("Error al verificar tus datos: " + e.getMessage());
                    mAuth.signOut(); // Desloguear de Auth
                });
    }

    private void toast(String s) {
        Toast.makeText(LoginActivity.this, s, Toast.LENGTH_LONG).show();
    }
    private void goToMain() {
        Intent i = new Intent(LoginActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
