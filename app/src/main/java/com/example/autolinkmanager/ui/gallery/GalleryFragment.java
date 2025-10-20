package com.example.autolinkmanager.ui.gallery;

import android.graphics.Color;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autolinkmanager.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GalleryFragment extends Fragment {

    private FirebaseFirestore db;

    private TextView tvTotalAgencias, tvServiciosPend, tvTotalPendienteMx;
    private MaterialCardView cardCrear, cardMapa;
    private RecyclerView rvMovs;
    private TextView tvNoMovs;

    private final List<RecentMovementItem> movementItems = new ArrayList<>();
    private RecentMovementsAdapter movementsAdapter;

    private final NumberFormat currencyMx = NumberFormat.getCurrencyInstance(new Locale("es","MX"));

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

        tvTotalAgencias   = v.findViewById(R.id.tv_total_agencias);
        tvServiciosPend   = v.findViewById(R.id.tv_servicios_pendientes);
        tvTotalPendienteMx= v.findViewById(R.id.tv_total_pendiente_mx);

        cardCrear         = v.findViewById(R.id.card_crear_agencia);
        cardMapa          = v.findViewById(R.id.card_mapa_agencias);

        rvMovs            = v.findViewById(R.id.recycler_view_movimientos);
        tvNoMovs          = v.findViewById(R.id.tv_no_movements);

        rvMovs.setLayoutManager(new LinearLayoutManager(getContext()));
        movementsAdapter = new RecentMovementsAdapter(movementItems);
        rvMovs.setAdapter(movementsAdapter);

        // Navegación a pantallas de admin
        if (cardCrear != null) {
            cardCrear.setOnClickListener(x ->
                    Navigation.findNavController(x).navigate(R.id.nav_create_agency));
        }
        if (cardMapa != null) {
            cardMapa.setOnClickListener(x ->
                    Navigation.findNavController(x).navigate(R.id.nav_agencies_map));
        }

        loadDashboard();
    }

    private void loadDashboard() {
        loadTotalAgencies();
        loadPendingServicesCount();
        loadPendingPaymentsSum();
        loadRecentServices();
    }

    // 1) Total de agencias (colección agencies)
    private void loadTotalAgencies() {
        db.collection("agencies")
                .get()
                .addOnSuccessListener(snap -> {
                    tvTotalAgencias.setText(String.valueOf(snap.size()));
                })
                .addOnFailureListener(e ->
                        toast("Error total agencias: " + e.getMessage()));
    }

    // 2) Servicios pendientes (global): vehicles con pagado == false
    private void loadPendingServicesCount() {
        db.collectionGroup("vehicles")
                .whereEqualTo("pagado", false)
                .get()
                .addOnSuccessListener(snap -> {
                    tvServiciosPend.setText(String.valueOf(snap.size()));
                })
                .addOnFailureListener(e ->
                        toast("Error servicios pendientes: " + e.getMessage()));
    }

    // 3) Total $ pendiente (global): suma costo donde pagado == false
    private void loadPendingPaymentsSum() {
        db.collectionGroup("vehicles")
                .whereEqualTo("pagado", false)
                .get()
                .addOnSuccessListener(snap -> {
                    double total = 0.0;
                    for (QueryDocumentSnapshot d : snap) {
                        Double costo = d.getDouble("costo");
                        if (costo != null) total += costo;
                    }
                    tvTotalPendienteMx.setText(currencyMx.format(total));
                })
                .addOnFailureListener(e ->
                        toast("Error total pendiente: " + e.getMessage()));
    }

    // 4) Últimas altas globales (ordenar por fechaIngreso)
    private void loadRecentServices() {
        movementItems.clear();
        db.collectionGroup("vehicles")
                .orderBy("fechaIngreso", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot d : snap) {
                        String placa  = d.getString("placa");
                        String modelo = d.getString("modelo");
                        Boolean pagado = d.getBoolean("pagado");
                        String status = (pagado != null && pagado) ? "Pagado" : "Pendiente";
                        movementItems.add(new RecentMovementItem(placa, modelo, status));
                    }
                    updateMovementsUI();
                })
                .addOnFailureListener(e -> {
                    tvNoMovs.setVisibility(View.VISIBLE);
                    rvMovs.setVisibility(View.GONE);
                    tvNoMovs.setText("Error al cargar altas.");
                });
    }

    private void updateMovementsUI() {
        if (movementItems.isEmpty()) {
            rvMovs.setVisibility(View.GONE);
            tvNoMovs.setVisibility(View.VISIBLE);
        } else {
            tvNoMovs.setVisibility(View.GONE);
            rvMovs.setVisibility(View.VISIBLE);
            movementsAdapter.notifyDataSetChanged();
        }
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show();
    }

    // --- modelos/adapter para el Recycler ---
    public static class RecentMovementItem {
        public final String placa;
        public final String modelo;
        public final String status;
        public RecentMovementItem(String placa, String modelo, String status) {
            this.placa = placa; this.modelo = modelo; this.status = status;
        }
    }

    public static class RecentMovementsAdapter extends RecyclerView.Adapter<RecentMovementsAdapter.VH> {
        private final List<RecentMovementItem> items;
        public RecentMovementsAdapter(List<RecentMovementItem> items) { this.items = items; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_recent_movement, p, false);
            return new VH(view);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            RecentMovementItem it = items.get(pos);
            h.tvPlaca.setText(it.placa != null ? it.placa : "—");
            h.tvModelo.setText(it.modelo != null ? it.modelo : "—");
            h.tvStatus.setText(it.status);
            if ("Pagado".equalsIgnoreCase(it.status)) {
                h.tvStatus.setBackgroundResource(R.drawable.rounded_tag_active);
                h.tvStatus.setTextColor(Color.WHITE);
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.rounded_tag_pending);
                h.tvStatus.setTextColor(Color.WHITE);
            }
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvPlaca, tvModelo, tvStatus;
            VH(@NonNull View v) {
                super(v);
                tvPlaca  = v.findViewById(R.id.tv_movement_plate);
                tvModelo = v.findViewById(R.id.tv_movement_model);
                tvStatus = v.findViewById(R.id.tv_movement_status);
            }
        }
    }
}
