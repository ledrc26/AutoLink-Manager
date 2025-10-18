package com.example.autolinkmanager.ui.admin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.autolinkmanager.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;

public class CreateAgencyFragment extends Fragment {

    private EditText etAgencyName, etPhone;
    private TextView tvLocationPreview;
    private Button btnPickLocation, btnCreateAgency;
    private ProgressBar progress;

    private FusedLocationProviderClient fused;
    private Double lat = null, lng = null;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_create_agency, container, false);

        etAgencyName = v.findViewById(R.id.etAgencyName);
        etPhone = v.findViewById(R.id.etPhone);
        tvLocationPreview = v.findViewById(R.id.tvLocationPreview);
        btnPickLocation = v.findViewById(R.id.btnPickLocation);
        btnCreateAgency = v.findViewById(R.id.btnCreateAgency);
        progress = v.findViewById(R.id.progress);

        fused = LocationServices.getFusedLocationProviderClient(requireActivity());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        btnPickLocation.setOnClickListener(view -> getCurrentLocation());
        btnCreateAgency.setOnClickListener(view -> createAgency());

        return v;
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }
        fused.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        lat = loc.getLatitude();
                        lng = loc.getLongitude();
                        tvLocationPreview.setText("Ubicación: " + lat + ", " + lng);
                    } else {
                        Toast.makeText(requireContext(), "No se obtuvo ubicación. Activa GPS", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error ubic.: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void createAgency() {
        String name = etAgencyName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name))  { etAgencyName.setError("Requerido"); return; }
        if (TextUtils.isEmpty(phone)) { etPhone.setError("Requerido"); return; }
        if (lat == null || lng == null) { Toast.makeText(requireContext(), "Selecciona ubicación", Toast.LENGTH_SHORT).show(); return; }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { Toast.makeText(requireContext(), "Sesión inválida", Toast.LENGTH_SHORT).show(); return; }

        progress.setVisibility(View.VISIBLE);
        btnCreateAgency.setEnabled(false);

        Map<String, Object> doc = new HashMap<>();
        doc.put("name", name);
        doc.put("phone", phone);
        doc.put("location", new GeoPoint(lat, lng));
        doc.put("createdBy", user.getUid());
        doc.put("createdAt", FieldValue.serverTimestamp());

        db.collection("agencies").add(doc)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(requireContext(), "Agencia creada", Toast.LENGTH_SHORT).show();
                    progress.setVisibility(View.GONE);
                    btnCreateAgency.setEnabled(true);
                    // Si quieres vincular un usuario-agencia: actualiza su user.agencyId
                    // db.collection("users").document(algunUid).update("agencyId", ref.getId());
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    btnCreateAgency.setEnabled(true);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(requestCode, perms, res);
        if (requestCode == 1001 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        }
    }
}

