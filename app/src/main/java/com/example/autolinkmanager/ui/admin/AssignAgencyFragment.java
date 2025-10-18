package com.example.autolinkmanager.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.autolinkmanager.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.util.*;

public class AssignAgencyFragment extends Fragment {

    private Spinner spUsers, spAgencies;
    private Button btnAssign;
    private ProgressBar progress;
    private TextView tvResult;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Listas en memoria para mapear selección -> ids
    private final List<String> userIds = new ArrayList<>();
    private final List<String> userLabels = new ArrayList<>();

    private final List<String> agencyIds = new ArrayList<>();
    private final List<String> agencyLabels = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_assign_agency, container, false);

        spUsers = v.findViewById(R.id.spUsers);
        spAgencies = v.findViewById(R.id.spAgencies);
        btnAssign = v.findViewById(R.id.btnAssign);
        progress = v.findViewById(R.id.progress);
        tvResult = v.findViewById(R.id.tvResult);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        enforceAdminOrPop();

        loadUsers();
        loadAgencies();

        btnAssign.setOnClickListener(view -> assignAgency());

        return v;
    }

    private void enforceAdminOrPop() {
        FirebaseUser u = mAuth.getCurrentUser();
        if (u == null) {
            toast("Inicia sesión");
            requireActivity().onBackPressed();
            return;
        }
        db.collection("users").document(u.getUid()).get()
                .addOnSuccessListener(s -> {
                    String role = s.getString("role");
                    if (!"admin".equalsIgnoreCase(role)) {
                        toast("Acceso solo para administradores");
                        requireActivity().onBackPressed();
                    }
                })
                .addOnFailureListener(e -> {
                    toast("No se pudo verificar rol");
                    requireActivity().onBackPressed();
                });
    }

    private void loadUsers() {
        progress.setVisibility(View.VISIBLE);
        // Trae todos los usuarios "agency" (puedes ajustar el filtro si deseas)
        db.collection("users")
                .whereEqualTo("role", "agency")
                .get()
                .addOnSuccessListener(snaps -> {
                    userIds.clear(); userLabels.clear();
                    for (DocumentSnapshot d : snaps) {
                        String uid = d.getString("uid");
                        String nombre = d.getString("nombre");
                        String correo = d.getString("correo");
                        if (TextUtils.isEmpty(uid)) uid = d.getId();
                        String label = (nombre != null ? nombre : "(Sin nombre)") + (correo != null ? " • " + correo : "");
                        userIds.add(uid);
                        userLabels.add(label);
                    }
                    spUsers.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, userLabels));
                    progress.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error cargando usuarios: " + e.getMessage());
                });
    }

    private void loadAgencies() {
        progress.setVisibility(View.VISIBLE);
        db.collection("agencies")
                .get()
                .addOnSuccessListener(snaps -> {
                    agencyIds.clear(); agencyLabels.clear();
                    for (DocumentSnapshot d : snaps) {
                        String id = d.getId();
                        String name = d.getString("name");
                        String phone = d.getString("phone");
                        String label = (name != null ? name : "(Sin nombre)") + (phone != null ? " • " + phone : "");
                        agencyIds.add(id);
                        agencyLabels.add(label);
                    }
                    spAgencies.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, agencyLabels));
                    progress.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error cargando agencias: " + e.getMessage());
                });
    }

    private void assignAgency() {
        if (userIds.isEmpty() || agencyIds.isEmpty()) {
            toast("Faltan usuarios o agencias");
            return;
        }
        int uPos = spUsers.getSelectedItemPosition();
        int aPos = spAgencies.getSelectedItemPosition();
        if (uPos < 0 || aPos < 0) { toast("Selecciona usuario y agencia"); return; }

        String uid = userIds.get(uPos);
        String agencyId = agencyIds.get(aPos);

        progress.setVisibility(View.VISIBLE);
        btnAssign.setEnabled(false);

        db.collection("users").document(uid)
                .update("agencyId", agencyId)
                .addOnSuccessListener(unused -> {
                    progress.setVisibility(View.GONE);
                    btnAssign.setEnabled(true);
                    String userLabel = userLabels.get(uPos);
                    String agencyLabel = agencyLabels.get(aPos);
                    tvResult.setText("Asignado: " + userLabel + " \n→ " + agencyLabel);
                    toast("Agencia asignada");
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    btnAssign.setEnabled(true);
                    toast("Error al asignar: " + e.getMessage());
                });
    }

    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }
}
