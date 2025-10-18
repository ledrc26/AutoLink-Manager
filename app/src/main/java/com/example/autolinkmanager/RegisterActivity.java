package com.example.autolinkmanager;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText etNombre, etEmail, etPassword;
    private Button btnRegister;
    private ProgressBar progress;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etNombre = findViewById(R.id.etNombre);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progress = findViewById(R.id.progress);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 🔍 Logea qué opciones de Firebase se están usando realmente en runtime
        try {
            FirebaseOptions opts = FirebaseApp.getInstance().getOptions();
            Log.d(TAG, "FirebaseOptions:");
            Log.d(TAG, "  projectId      = " + opts.getProjectId());
            Log.d(TAG, "  applicationId  = " + opts.getApplicationId()); // == mobilesdk_app_id en google-services.json
            Log.d(TAG, "  apiKey         = " + opts.getApiKey());
            Log.d(TAG, "  storageBucket  = " + opts.getStorageBucket());
        } catch (Exception e) {
            Log.e(TAG, "No se pudieron obtener FirebaseOptions", e);
        }

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String nombre = etNombre.getText().toString().trim();
        String email  = etEmail.getText().toString().trim();
        String pass   = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(nombre)) { etNombre.setError("Requerido"); return; }
        if (TextUtils.isEmpty(email))  { etEmail.setError("Requerido");  return; }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) { etPassword.setError("Mínimo 6 caracteres"); return; }

        progress.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // 🔍 Paso 1: probar si Auth responde para este email
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "fetchSignInMethods error", task.getException());
                    } else {
                        Log.d(TAG, "fetchSignInMethods OK: " + task.getResult().getSignInMethods());
                    }
                });

        // 🔍 Paso 2: crear usuario
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override public void onComplete(@NonNull Task<AuthResult> task) {
                        progress.setVisibility(View.GONE);
                        btnRegister.setEnabled(true);

                        if (task.isSuccessful()) {
                            FirebaseUser fUser = mAuth.getCurrentUser();
                            if (fUser == null) {
                                fail("Usuario no disponible");
                                return;
                            }
                            String uid = fUser.getUid();

                            // Guardar perfil en Firestore
                            User user = new User(uid, email, nombre, "agency"); // <-- por defecto agency
                            db.collection("users").document(uid).set(user)
                                    .addOnSuccessListener(unused -> {
                                        Log.d(TAG, "Perfil guardado en Firestore");
                                        Toast.makeText(RegisterActivity.this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show();
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error guardando perfil", e);
                                        fail("Error guardando perfil: " + e.getMessage());
                                    });

                        } else {
                            Exception e = task.getException();
                            if (e instanceof FirebaseAuthException) {
                                FirebaseAuthException fae = (FirebaseAuthException) e;
                                Log.e(TAG, "Auth errorCode=" + fae.getErrorCode() + " msg=" + fae.getMessage(), e);
                                // Mensaje más claro en UI
                                if ("ERROR_INTERNAL_ERROR".equals(fae.getErrorCode())) {
                                    fail("Error interno de configuración en Firebase Auth (reCAPTCHA/Play Integrity). Revisa SHA-256 y Play Services.");
                                } else {
                                    fail(fae.getMessage());
                                }
                            } else {
                                Log.e(TAG, "Error desconocido al registrar", e);
                                fail(e != null ? e.getMessage() : "Error al registrar");
                            }
                        }
                    }
                });
    }

    private void fail(String msg) {
        progress.setVisibility(View.GONE);
        btnRegister.setEnabled(true);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        Log.w(TAG, "Fallo en registro: " + msg);
    }
}
