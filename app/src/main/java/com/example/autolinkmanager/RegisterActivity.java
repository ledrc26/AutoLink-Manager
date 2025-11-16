package com.example.autolinkmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import java.util.*;

// Activity de Registro con PRE-REGISTRO y selector de agencia
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText etNombre, etEmail, etPassword;
    private Button btnRegister;
    private ProgressBar progress;
    private Spinner spAgency;
    private TextView tvNoAgencies;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Lista y adapter para el Spinner
    private ArrayAdapter<AgencyOption> agencyAdapter;
    private final List<AgencyOption> agenciesFree = new ArrayList<>();

    // Clase simple para el Spinner
    public static class AgencyOption {
        public final String id;
        public final String label;
        public AgencyOption(String id, String label) {
            this.id = id; this.label = label;
        }
        @Override public String toString() { return label; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etNombre = findViewById(R.id.etNombre);
        etEmail  = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progress = findViewById(R.id.progress);
        spAgency = findViewById(R.id.spAgency);
        tvNoAgencies = findViewById(R.id.tvNoAgencies);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // (Opcional) Log para diagnosticar la configuración de Firebase en runtime
        try {
            FirebaseOptions opts = FirebaseApp.getInstance().getOptions();
            Log.d(TAG, "FirebaseOptions:");
            Log.d(TAG, "  projectId      = " + opts.getProjectId());
            Log.d(TAG, "  applicationId  = " + opts.getApplicationId());
            Log.d(TAG, "  apiKey         = " + opts.getApiKey());
            Log.d(TAG, "  storageBucket  = " + opts.getStorageBucket());
        } catch (Exception e) {
            Log.e(TAG, "No se pudieron obtener FirebaseOptions", e);
        }

        // Configurar Spinner
        agencyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, agenciesFree);
        spAgency.setAdapter(agencyAdapter);

        // Cargar agencias con userID == null
        loadAvailableAgencies();

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void loadAvailableAgencies() {
        progress.setVisibility(View.VISIBLE);
        db.collection("agencies")
                .whereEqualTo("userID", null) // IMPORTANTE: el campo DEBE existir y ser null
                .get()
                .addOnCompleteListener(this::onAgenciesLoaded);
    }

    private void onAgenciesLoaded(@NonNull Task<QuerySnapshot> task) {
        progress.setVisibility(View.GONE);
        if (!task.isSuccessful()) {
            Exception e = task.getException();
            Toast.makeText(this, "Error cargando agencias: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
            Log.e(TAG, "loadAvailableAgencies error", e);
            return;
        }
        QuerySnapshot snap = task.getResult();
        agenciesFree.clear();

        if (snap != null) {
            for (DocumentSnapshot d : snap) {
                String id = d.getId();
                String name = d.getString("name");
                agenciesFree.add(new AgencyOption(id, name != null ? name : "(Sin nombre)"));
            }
        }

        agencyAdapter.notifyDataSetChanged();
        tvNoAgencies.setVisibility(agenciesFree.isEmpty() ? View.VISIBLE : View.GONE);
        spAgency.setEnabled(!agenciesFree.isEmpty());
        btnRegister.setEnabled(!agenciesFree.isEmpty());
    }

    private void doRegister() {
        String nombre = etNombre.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(nombre)) { etNombre.setError("Requerido"); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Requerido"); return; }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            etPassword.setError("Mínimo 6 caracteres"); return;
        }
        if (spAgency.getSelectedItem() == null) {
            Toast.makeText(this, "Selecciona una agencia disponible", Toast.LENGTH_LONG).show();
            return;
        }
        AgencyOption selected = (AgencyOption) spAgency.getSelectedItem();

        progress.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // Crear usuario en Auth
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, authTask -> {
                    if (!authTask.isSuccessful()) {
                        progress.setVisibility(View.GONE);
                        btnRegister.setEnabled(true);
                        handleAuthError(authTask.getException());
                        return;
                    }

                    FirebaseUser fUser = mAuth.getCurrentUser();
                    if (fUser == null) {
                        progress.setVisibility(View.GONE);
                        btnRegister.setEnabled(true);
                        fail("Usuario no disponible");
                        return;
                    }
                    String uid = fUser.getUid();

                    // Crear perfil de usuario
                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("uid", uid);
                    userDoc.put("email", email);
                    userDoc.put("nombre", nombre);
                    userDoc.put("role", "pending");
                    userDoc.put("requestedAgencyId", selected.id);
                    userDoc.put("agencyId", null);
                    userDoc.put("isActive", false);

                    db.collection("users").document(uid).set(userDoc)
                            .addOnSuccessListener(unused -> {
                                // Crear solicitud para auditoría
                                Map<String, Object> req = new HashMap<>();
                                req.put("userId", uid);
                                req.put("agencyId", selected.id);

                                db.collection("agency_requests").add(req)
                                        .addOnSuccessListener(r -> {
                                            // 🔥 CERRAR SESIÓN Y REDIRIGIR AL LOGIN
                                            mAuth.signOut();
                                            progress.setVisibility(View.GONE);
                                            btnRegister.setEnabled(true);
                                            Toast.makeText(this, "Pre-registro exitoso. Ahora inicia sesión con tus credenciales.", Toast.LENGTH_LONG).show();

                                            // Redirigir a LoginActivity
                                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            // Aún así cerrar sesión aunque falle la solicitud
                                            mAuth.signOut();
                                            progress.setVisibility(View.GONE);
                                            btnRegister.setEnabled(true);
                                            Log.e(TAG, "Error creando agency_request", e);

                                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                            finish();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                // Si falla guardar el perfil, también cerrar sesión
                                if (mAuth.getCurrentUser() != null) {
                                    mAuth.signOut();
                                }
                                progress.setVisibility(View.GONE);
                                btnRegister.setEnabled(true);
                                Log.e(TAG, "Error guardando perfil", e);
                                fail("Error guardando perfil: " + e.getMessage());
                            });
                });
    }
    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthException) {
            FirebaseAuthException fae = (FirebaseAuthException) e;
            Log.e(TAG, "Auth errorCode=" + fae.getErrorCode() + " msg=" + fae.getMessage(), e);
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

    private void fail(String msg) {
        progress.setVisibility(View.GONE);
        btnRegister.setEnabled(true);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        Log.w(TAG, "Fallo en registro: " + msg);
    }
}
