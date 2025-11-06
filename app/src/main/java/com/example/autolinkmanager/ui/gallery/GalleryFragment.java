package com.example.autolinkmanager.ui.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.autolinkmanager.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GalleryFragment extends Fragment {

    private FirebaseFirestore db;

    private TextView tvTotalAgencias, tvServiciosPend;
    private MaterialCardView cardCrear, cardMapa;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        tvTotalAgencias = v.findViewById(R.id.tv_total_agencias);
        tvServiciosPend = v.findViewById(R.id.tv_servicios_pendientes);

        cardCrear = v.findViewById(R.id.card_crear_agencia);
        cardMapa  = v.findViewById(R.id.card_mapa_agencias);

        // Navegación (se conserva tal cual)
        if (cardCrear != null) {
            cardCrear.setOnClickListener(x ->
                    Navigation.findNavController(x).navigate(R.id.nav_create_agency)
            );
        }
        if (cardMapa != null) {
            cardMapa.setOnClickListener(x ->
                    Navigation.findNavController(x).navigate(R.id.nav_agencies_map)
            );
        }

        loadMetrics();
    }

    private void loadMetrics() {
        loadTotalAgencies();
        loadPendingServicesWithoutIndexAtomic();
    }

    // Total de agencias
    private void loadTotalAgencies() {
        db.collection("agencies")
                .get()
                .addOnSuccessListener(snap ->
                        tvTotalAgencias.setText(String.valueOf(snap.size()))
                )
                .addOnFailureListener(e -> toast("Error total agencias: " + e.getMessage()));
    }

    // Servicios pendientes: vehicles con pagado == false (en todas las agencias)
    private void loadPendingServicesWithoutIndexAtomic() {
        tvServiciosPend.setText("0");
        TextView tvTotalPendienteMx = getView().findViewById(R.id.tv_total_pendiente_mx);
        if (tvTotalPendienteMx != null) tvTotalPendienteMx.setText("$0");

        db.collection("agencies").get()
                .addOnSuccessListener(agenciesSnap -> {
                    int n = agenciesSnap.size();
                    if (n == 0) {
                        tvServiciosPend.setText("0");
                        if (tvTotalPendienteMx != null) tvTotalPendienteMx.setText("$0");
                        return;
                    }

                    AtomicInteger totalCount = new AtomicInteger(0);
                    AtomicReference<Double> totalMonto = new AtomicReference<>(0.0);
                    AtomicInteger remaining = new AtomicInteger(n);

                    for (DocumentSnapshot agency : agenciesSnap.getDocuments()) {
                        db.collection("agencies").document(agency.getId())
                                .collection("vehicles")
                                .whereEqualTo("pagado", false)
                                .get()
                                .addOnSuccessListener(qs -> {
                                    // suma cantidad de servicios
                                    totalCount.addAndGet(qs.size());
                                    // suma el costo total
                                    double subtotal = 0.0;
                                    for (DocumentSnapshot doc : qs.getDocuments()) {
                                        Double costo = doc.getDouble("costo");
                                        if (costo != null) subtotal += costo;
                                    }
                                    totalMonto.set(totalMonto.get() + subtotal);

                                    // cuando terminen todas las agencias
                                    if (remaining.decrementAndGet() == 0) {
                                        tvServiciosPend.setText(String.valueOf(totalCount.get()));

                                        if (tvTotalPendienteMx != null) {
                                            java.text.NumberFormat mxn = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es", "MX"));
                                            tvTotalPendienteMx.setText(mxn.format(totalMonto.get()));
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (remaining.decrementAndGet() == 0) {
                                        tvServiciosPend.setText(String.valueOf(totalCount.get()));
                                        if (tvTotalPendienteMx != null) {
                                            java.text.NumberFormat mxn = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es", "MX"));
                                            tvTotalPendienteMx.setText(mxn.format(totalMonto.get()));
                                        }
                                    }
                                    toast("Error en agencia: " + e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    toast("Error listando agencias: " + e.getMessage());
                    tvServiciosPend.setText("0");
                    if (tvTotalPendienteMx != null) tvTotalPendienteMx.setText("$0");
                });
    }

    private void toast(String m) {
        if (getContext() != null) {
            Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show();
        }
    }
}