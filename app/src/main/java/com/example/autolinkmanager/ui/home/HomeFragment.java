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
import com.example.autolinkmanager.databinding.FragmentHomeBinding;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentAgencyId;
    private TextView tv_agency_name_title;
    // UI Views
    private TextView tvVehiculosRegistradosValue, tvServiciosEnCursoValue, tvPagosPendientesValue;
    private MaterialCardView cardAltaVehiculo, cardVerServicios, cardCobros;
    private RecyclerView recyclerViewMovimientos; // RecyclerView para movimientos
    private TextView tvNoMovements; // Mensaje si no hay movimientos

    // Adapter para RecyclerView
    private RecentMovementsAdapter movementsAdapter;
    private List<RecentMovementItem> movementItems;

    public HomeFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        movementItems = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Infla tu layout editado (asumiendo que se llama fragment_home.xml)
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar Vistas de Estadísticas
        // BIEN (IDs nuevos del XML)
        tvVehiculosRegistradosValue = view.findViewById(R.id.tv_vehiculos_registrados_value);
        tvServiciosEnCursoValue = view.findViewById(R.id.tv_servicios_en_curso_value);
        tvPagosPendientesValue = view.findViewById(R.id.tv_pagos_pendientes_value);


        // Inicializar Vistas de Acciones (CardViews)
        cardAltaVehiculo = view.findViewById(R.id.card_alta_vehiculo); // Necesitas añadir IDs
        cardVerServicios = view.findViewById(R.id.card_ver_servicios); // Necesitas añadir IDs

        // Inicializar RecyclerView y Adapter
        recyclerViewMovimientos = view.findViewById(R.id.recycler_view_movimientos); // Necesitas añadir ID al LinearLayout contenedor
        tvNoMovements = view.findViewById(R.id.tv_no_movements);             // Añade un TextView para este mensaje en tu layout
        tv_agency_name_title = view.findViewById(R.id.tv_agency_name_title);
        recyclerViewMovimientos.setLayoutManager(new LinearLayoutManager(getContext()));
        movementsAdapter = new RecentMovementsAdapter(movementItems);
        recyclerViewMovimientos.setAdapter(movementsAdapter);

        // Configurar Navegación
        setupNavigation(view);

        // Cargar datos
        loadUserAgencyId();
    }

    // Método para añadir IDs a las vistas si no los pusiste en el XML
    private void findViewsById(View view) {
        // Asumiendo que los TextViews están dentro de los LinearLayouts de las StatCards
        View cardVehiculosView = view.findViewById(R.id.card_vehiculos); // Añade este ID al CardView
        if (cardVehiculosView != null) {
            // Busca dentro del CardView. Ajusta si la estructura es diferente
            LinearLayout layout = (LinearLayout) ((ViewGroup) cardVehiculosView).getChildAt(0);
            // Usa el ID correcto del XML y la variable correcta si la renombraste
            tvVehiculosRegistradosValue = view.findViewById(R.id.tv_vehiculos_registrados_value);// Añade este ID al TextView del valor
        }
        // Repite para Servicios y Pagos...

        cardAltaVehiculo = view.findViewById(R.id.card_alta_vehiculo); // Añade IDs a las ActionCards
        cardVerServicios = view.findViewById(R.id.card_ver_servicios);

        recyclerViewMovimientos = view.findViewById(R.id.recycler_view_movimientos); // Añade ID al LinearLayout de movimientos
        tvNoMovements = view.findViewById(R.id.tv_no_movements); // Añade este TextView bajo el título "Últimos movimientos"
    }

    // Configura los listeners para navegar
    private void setupNavigation(View view) {
        NavController navController = Navigation.findNavController(view);

        if (cardAltaVehiculo != null) {
            cardAltaVehiculo.setOnClickListener(v -> {
                // Navega a AddAutoFragment (Usa el ID de acción de tu mobile_navigation.xml)
                navController.navigate(R.id.action_nav_home_to_nav_add_car);
            });
        }

        if (cardVerServicios != null) {
            cardVerServicios.setOnClickListener(v -> {
                // Navega a CarListFragment (Usa el ID de acción de tu mobile_navigation.xml)
                navController.navigate(R.id.action_nav_home_to_nav_car_list);
            });
        }

        if (cardCobros != null) {
            cardCobros.setOnClickListener(v -> {
                // Navega a un futuro Fragmento de Cobros (Define la acción en mobile_navigation.xml)
                Toast.makeText(getContext(), "Funcionalidad 'Cobros' no implementada", Toast.LENGTH_SHORT).show();
                // navController.navigate(R.id.action_nav_home_to_nav_cobros);
            });
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
                                    Log.d(TAG, "Agency ID: " + currentAgencyId);

                                    // Carga las estadísticas y movimientos
                                    loadDashboardStats();
                                    loadRecentMovements();

                                    // ¡AQUÍ ESTÁ EL CAMBIO!
                                    loadAgencyName(currentAgencyId);

                                } else { /* Manejar usuario sin agencyId */ }
                            } else { /* Manejar documento de usuario no encontrado */ }
                        } else { /* Manejar error al cargar usuario */ }
                    });
        } else { /* Manejar usuario no logueado */ }
    }

    // Carga las estadísticas (conteos) desde Firebase
    private void loadDashboardStats() {
        if (currentAgencyId == null) return;

        // 1. Contar Vehículos
        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .count() // Usa la agregación count() para eficiencia
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        long count = task.getResult().getCount();
                        if (tvVehiculosRegistradosValue != null) tvVehiculosRegistradosValue.setText(String.valueOf(count));
                    } else {
                        Log.e(TAG, "Error contando vehículos", task.getException());
                    }
                });

        // 2. Contar Servicios en Curso y Pagos Pendientes (Requiere consulta)
        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .whereEqualTo("pagado", false) // Asume que "en curso" = "no pagado"
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int countPendientes = task.getResult().size();
                        if (tvServiciosEnCursoValue != null) tvServiciosEnCursoValue.setText(String.valueOf(countPendientes)); // O ajusta la lógica si "en curso" es diferente
                        if (tvPagosPendientesValue != null) tvPagosPendientesValue.setText(String.valueOf(countPendientes));
                    } else {
                        Log.e(TAG, "Error contando servicios pendientes", task.getException());
                    }
                });

        // Nota: Contar "Servicios en curso" podría ser más complejo si un vehículo puede tener
        // múltiples servicios o si "en curso" no es lo mismo que "no pagado".
        // Esta es una implementación simplificada.
    }

    // Carga los últimos movimientos (vehículos/servicios recientes)
    private void loadRecentMovements() {
        if (currentAgencyId == null) return;

        movementItems.clear(); // Limpiar lista

        // Carga los últimos 5 vehículos modificados/creados (ajusta el campo si tienes timestamp)
        // O podrías ordenar por fechaIngreso si ese campo siempre existe
        db.collection("agencies").document(currentAgencyId).collection("vehicles")
                .orderBy("fechaIngreso", Query.Direction.DESCENDING) // Ordena por fecha de ingreso descendente
                .limit(5) // Limita a los 5 más recientes
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String placa = document.getString("placa");
                            String modelo = document.getString("modelo");
                            Boolean pagado = document.getBoolean("pagado");
                            String status = (pagado != null && pagado) ? "Pagado" : "Pendiente";

                            // Necesitas crear esta clase simple
                            movementItems.add(new RecentMovementItem(placa, modelo, status));
                        }

                        // Actualizar UI
                        if (movementItems.isEmpty()) {
                            recyclerViewMovimientos.setVisibility(View.GONE);
                            if (tvNoMovements != null) tvNoMovements.setVisibility(View.VISIBLE);
                        } else {
                            recyclerViewMovimientos.setVisibility(View.VISIBLE);
                            if (tvNoMovements != null) tvNoMovements.setVisibility(View.GONE);
                            movementsAdapter.notifyDataSetChanged();
                        }

                    } else {
                        Log.e(TAG, "Error cargando movimientos recientes", task.getException());
                        recyclerViewMovimientos.setVisibility(View.GONE);
                        if (tvNoMovements != null) {
                            tvNoMovements.setVisibility(View.VISIBLE);
                            tvNoMovements.setText("Error al cargar movimientos.");
                        }
                    }
                });
    }

    // --- Clase de datos simple para los items de movimientos ---
    public static class RecentMovementItem {
        private String placa;
        private String modelo;
        private String status;

        public RecentMovementItem(String placa, String modelo, String status) {
            this.placa = placa;
            this.modelo = modelo;
            this.status = status;
        }

        public String getPlaca() { return placa; }
        public String getModelo() { return modelo; }
        public String getStatus() { return status; }
    }

    // --- Adapter para el RecyclerView de movimientos ---
    public static class RecentMovementsAdapter extends RecyclerView.Adapter<RecentMovementsAdapter.ViewHolder> {

        private List<RecentMovementItem> items;

        public RecentMovementsAdapter(List<RecentMovementItem> items) {
            this.items = items;
        }

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

            // Poner el fondo correcto al tag de estado
            if ("Pagado".equalsIgnoreCase(item.getStatus())) {
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_tag_active);
                holder.tvStatus.setTextColor(Color.WHITE); // Asegúrate que el texto sea visible
            } else { // Pendiente
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_tag_pending);
                holder.tvStatus.setTextColor(Color.WHITE); // Asegúrate que el texto sea visible
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

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
    private void loadAgencyName(String agencyId) {
        if (agencyId == null || tv_agency_name_title == null) return;

        db.collection("agencies").document(agencyId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String agencyName = documentSnapshot.getString("name");
                        if (agencyName != null && !agencyName.isEmpty()) {
                            tv_agency_name_title.setText(agencyName);
                        } else {
                            tv_agency_name_title.setText("Agencia sin nombre");
                        }
                    } else {
                        Log.w(TAG, "No se encontró el documento de la agencia: " + agencyId);
                        tv_agency_name_title.setText("Agencia no encontrada");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar nombre de agencia", e);
                    tv_agency_name_title.setText("Error");
                });
    }
}