package com.example.autolinkmanager.ui.admin;

import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autolinkmanager.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import java.util.*;

public class ManageAgenciesFragment extends Fragment {

    private RecyclerView rv;
    private ProgressBar progress;
    private AgenciesAdapter adapter;

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ListenerRegistration liveListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_manage_agencies, container, false);
        rv = v.findViewById(R.id.rvAgencies);
        progress = v.findViewById(R.id.progress);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AgenciesAdapter(new ArrayList<>(), new AgenciesAdapter.OnAgencyAction() {
            @Override public void onEdit(DocumentSnapshot doc) { showEditDialog(doc); }
            @Override public void onDelete(DocumentSnapshot doc) { confirmDelete(doc); }
        });
        rv.setAdapter(adapter);

        enforceAdminOrPop();
        subscribeRealtime();

        return v;
    }

    private void enforceAdminOrPop() {
        FirebaseUser u = mAuth.getCurrentUser();
        if (u == null) { pop("Inicia sesión"); return; }
        db.collection("users").document(u.getUid()).get()
                .addOnSuccessListener(s -> {
                    String role = s.getString("role");
                    if (!"admin".equalsIgnoreCase(role)) pop("Acceso solo para administradores");
                })
                .addOnFailureListener(e -> pop("No se pudo verificar rol"));
    }

    private void pop(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        requireActivity().onBackPressed();
    }

    private void subscribeRealtime() {
        progress.setVisibility(View.VISIBLE);
        liveListener = db.collection("agencies")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snaps, e) -> {
                    progress.setVisibility(View.GONE);
                    if (e != null) {
                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snaps == null) return;
                    adapter.setData(snaps.getDocuments());
                });
    }

    private void showEditDialog(DocumentSnapshot doc) {
        String currentName = doc.getString("name");
        String currentPhone = doc.getString("phone");

        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_agency, null, false);
        EditText etName = content.findViewById(R.id.etName);
        EditText etPhone = content.findViewById(R.id.etPhone);

        etName.setText(currentName != null ? currentName : "");
        etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        etPhone.setText(currentPhone != null ? currentPhone : "");

        new AlertDialog.Builder(requireContext())
                .setTitle("Editar agencia")
                .setView(content)
                .setPositiveButton("Guardar", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    String newPhone = etPhone.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Snackbar.make(rv, "El nombre es requerido", Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> update = new HashMap<>();
                    update.put("name", newName);
                    update.put("phone", newPhone);
                    doc.getReference().update(update)
                            .addOnSuccessListener(unused -> Snackbar.make(rv, "Agencia actualizada", Snackbar.LENGTH_SHORT).show())
                            .addOnFailureListener(err -> Snackbar.make(rv, "Error: " + err.getMessage(), Snackbar.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmDelete(DocumentSnapshot doc) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Borrar agencia")
                .setMessage("¿Seguro que quieres borrar esta agencia?")
                .setPositiveButton("Borrar", (d, w) -> {
                    doc.getReference().delete()
                            .addOnSuccessListener(unused -> Snackbar.make(rv, "Agencia borrada", Snackbar.LENGTH_SHORT).show())
                            .addOnFailureListener(err -> Snackbar.make(rv, "Error: " + err.getMessage(), Snackbar.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (liveListener != null) liveListener.remove();
    }
}
