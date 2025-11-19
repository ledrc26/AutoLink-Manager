package com.example.autolinkmanager.ui.home;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autolinkmanager.R;
import com.example.autolinkmanager.User;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentAgencyId;

    // UI Views
    private TextView tv_agency_name_title;
    private TextView tvVehiculosRegistradosValue, tvServiciosEnCursoValue, tvPagosPendientesValue;
    private TextView tvGananciasTotalesValue; // NUEVO

    private MaterialCardView cardAltaVehiculo, cardVerServicios;
    private RecyclerView recyclerViewMovimientos;
    private TextView tvNoMovements;

    // Adapter
    private RecentMovementsAdapter movementsAdapter;
    private List<RecentMovementItem> movementItems;

    // Formateador de moneda
    private NumberFormat currencyFormatter;

    public HomeFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        movementItems = new ArrayList<>();
        currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar Estadísticas
        tvVehiculosRegistradosValue = view.findViewById(R.id.tv_vehiculos_registrados_value);
        tvServiciosEnCursoValue = view.findViewById(R.id.tv_servicios_en_curso_value);
        tvPagosPendientesValue = view.findViewById(R.id.tv_pagos_pendientes_value);
        tvGananciasTotalesValue = view.findViewById(R.id.tv_ganancias_totales_value); // NUEVO

        // Inicializar Título
        tv_agency_name_title = view.findViewById(R.id.tv_agency_name_title);

        // Inicializar Cards de Acción
        cardAltaVehiculo = view.findViewById(R.id.card_alta_vehiculo);
        cardVerServicios = view.findViewById(R.id.card_ver_servicios);

        // Inicializar Lista Movimientos
        recyclerViewMovimientos = view.findViewById(R.id.recycler_view_movimientos);
        tvNoMovements = view.findViewById(R.id.tv_no_movements);

        recyclerViewMovimientos.setLayoutManager(new LinearLayoutManager(getContext()));
        movementsAdapter = new RecentMovementsAdapter(movementItems);
        recyclerViewMovimientos.setAdapter(movementsAdapter);

        // Configurar Navegación
        setupNavigation(view);

        // Cargar datos
        loadUserAgencyId();
    }

    private void setupNavigation(View view) {
        NavController navController = Navigation.findNavController(view);

        if (cardAltaVehiculo != null) {
            cardAltaVehiculo.setOnClickListener(v ->
                    navController.navigate(R.id.action_nav_home_to_nav_add_car));
        }

        if (cardVerServicios != null) {
            cardVerServicios.setOnClickListener(v ->
                    navController.navigate(R.id.action_nav_home_to_nav_car_list));
        }
    }

    private void loadUserAgencyId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            db.collection("users").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document != null && document.exists()) {
                                User user = document.toObject(User.class);
                                if (user != null && user.getAgencyId() != null) {
                                    currentAgencyId = user.getAgencyId();

                                    loadAgencyName(currentAgencyId);
                                    loadDashboardStats();
                                    loadRecentMovements();

                                } else { Log.w(TAG, "Usuario sin agencyId"); }
                            } else { Log.w(TAG, "Usuario no encontrado"); }
                        } else { Log.e(TAG, "Error loading user", task.getException()); }
                    });
        }
    }

    private void loadAgencyName(String agencyId) {
        if (agencyId == null || tv_agency_name_title == null) return;
        db.collection("agencies").document(agencyId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("name");
                        tv_agency_name_title.setText(name != null ? name : "Mi Agencia");
                    }
                });
    }

    // --- AQUÍ ESTÁ LA LÓGICA MODIFICADA ---
    private void loadDashboardStats() {
        if (currentAgencyId == null) return;

        // 1. Vehículos Registrados (Total)
        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .count().get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (tvVehiculosRegistradosValue != null)
                        tvVehiculosRegistradosValue.setText(String.valueOf(snapshot.getCount()));
                });

        // 2. Servicios en Curso (isFinished == false) -> REQUERIMIENTO CUMPLIDO
        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .whereEqualTo("isFinished", false)
                .count().get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (tvServiciosEnCursoValue != null)
                        tvServiciosEnCursoValue.setText(String.valueOf(snapshot.getCount()));
                });

        // 3. Pagos Pendientes (pagado == false)
        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .whereEqualTo("pagado", false)
                .count().get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (tvPagosPendientesValue != null)
                        tvPagosPendientesValue.setText(String.valueOf(snapshot.getCount()));
                });

        // 4. Ganancias Totales (Suma de 'costo' donde pagado == true) -> NUEVO
        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .whereEqualTo("pagado", true)
                .get() // Traemos los documentos para sumar (Firestore no tiene suma directa gratuita simple en Android antiguo)
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalGanancias = 0.0;
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Double costo = doc.getDouble("costo");
                        if (costo != null) {
                            totalGanancias += costo;
                        }
                    }
                    if (tvGananciasTotalesValue != null) {
                        tvGananciasTotalesValue.setText(currencyFormatter.format(totalGanancias));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error calculando ganancias", e));
    }

    private void loadRecentMovements() {
        if (currentAgencyId == null) return;

        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .orderBy("fechaIngreso", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    movementItems.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String placa = doc.getString("placa");
                        String modelo = doc.getString("modelo");

                        Boolean isFinishedObj = doc.getBoolean("isFinished");
                        boolean isFinished = isFinishedObj != null && isFinishedObj;

                        // Mostrar estado basado en si terminó o no
                        String status = isFinished ? "Terminado" : "En Proceso";

                        movementItems.add(new RecentMovementItem(placa, modelo, status));
                    }

                    if (movementItems.isEmpty()) {
                        recyclerViewMovimientos.setVisibility(View.GONE);
                        if (tvNoMovements != null) tvNoMovements.setVisibility(View.VISIBLE);
                    } else {
                        recyclerViewMovimientos.setVisibility(View.VISIBLE);
                        if (tvNoMovements != null) tvNoMovements.setVisibility(View.GONE);
                        movementsAdapter.notifyDataSetChanged();
                    }
                });
    }

    // --- Clases internas ---

    public static class RecentMovementItem {
        private String placa, modelo, status;
        public RecentMovementItem(String placa, String modelo, String status) {
            this.placa = placa; this.modelo = modelo; this.status = status;
        }
        public String getPlaca() { return placa; }
        public String getModelo() { return modelo; }
        public String getStatus() { return status; }
    }

    public static class RecentMovementsAdapter extends RecyclerView.Adapter<RecentMovementsAdapter.ViewHolder> {
        private List<RecentMovementItem> items;
        public RecentMovementsAdapter(List<RecentMovementItem> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_movement, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RecentMovementItem item = items.get(position);
            holder.tvPlaca.setText(item.getPlaca());
            holder.tvModelo.setText(item.getModelo());
            holder.tvStatus.setText(item.getStatus());

            if ("Terminado".equalsIgnoreCase(item.getStatus())) {
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_tag_active); // Verde
            } else {
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_tag_pending); // Rojo/Naranja
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlaca, tvModelo, tvStatus;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPlaca = itemView.findViewById(R.id.tv_movement_plate);
                tvModelo = itemView.findViewById(R.id.tv_movement_model);
                tvStatus = itemView.findViewById(R.id.tv_movement_status);
            }
        }
    }
}