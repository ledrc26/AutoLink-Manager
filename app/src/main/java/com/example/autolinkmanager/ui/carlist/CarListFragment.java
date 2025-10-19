package com.example.autolinkmanager.ui.carlist;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autolinkmanager.R;
import com.example.autolinkmanager.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

interface OnCarClickListener {
    void onCarClick(String vehicleDocId);
}

public class CarListFragment extends Fragment implements OnCarClickListener {

    private static final String TAG = "CarListFragment";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentAgencyId;

    private RecyclerView recyclerView;
    private CarAdapter carAdapter;
    private List<CarListItem> carListItems;
    private List<CarListItem> allCarListItems; // Lista original para la búsqueda
    private SearchView searchView;

    public CarListFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        carListItems = new ArrayList<>();
        allCarListItems = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_car_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view_car_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        carAdapter = new CarAdapter(carListItems, this); // 'this' es el listener
        recyclerView.setAdapter(carAdapter);

        searchView = view.findViewById(R.id.search_view_car_list);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterCars(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterCars(newText);
                return true;
            }
        });

        loadUserAgencyId();
    }

    private void loadUserAgencyId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            db.collection("users").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                User user = document.toObject(User.class);
                                if (user != null && user.getAgencyId() != null) {
                                    currentAgencyId = user.getAgencyId();
                                    loadCars(); // Cargar los autos después de obtener el agencyId
                                } else {
                                    Toast.makeText(getContext(), "Usuario sin ID de agencia.", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), "Documento de usuario no encontrado.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e(TAG, "Error al cargar usuario", task.getException());
                            Toast.makeText(getContext(), "Error al cargar datos de usuario.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(getContext(), "Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            // Considerar navegar a la pantalla de login si el usuario no está autenticado
        }
    }

    private void loadCars() {
        if (currentAgencyId == null) {
            Log.e(TAG, "No se pudo cargar agencyId, no se cargarán los autos.");
            return;
        }

        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles")
                .orderBy("placa", Query.Direction.ASCENDING) // Ordenar por placa
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        carListItems.clear();
                        allCarListItems.clear();
                        for (QueryDocumentSnapshot document : Objects.requireNonNull(task.getResult())) {
                            // Convertir a Auto, Mantenimiento o Hojalateria
                            // Solo necesitamos la placa, modelo, año y el estado de pago del último servicio

                            // Asumimos que los documentos de vehicles son de tipo Mantenimiento o Hojalateria
                            // y que solo hay un "servicio activo" por vehículo en esta colección.
                            // Si guardas diferentes tipos en la misma colección, esto necesitará más lógica
                            // para determinar el tipo de servicio y su estado.

                            // Por simplicidad, leeremos los campos comunes
                            String placa = document.getString("placa");
                            String modelo = document.getString("modelo");
                            Long anioLong = document.getLong("anio"); // Lee directamente como Long
                            int anio = (anioLong != null) ? anioLong.intValue() : 0;

                            Boolean pagado = document.getBoolean("pagado"); // Campo común en Mantenimiento y Hojalateria
                            String serviceStatus = (pagado != null && pagado) ? "Servicio Activo" : "Pago Pendiente"; // Ajusta la lógica si "Activo" significa otra cosa

                            CarListItem item = new CarListItem(document.getId(), placa, modelo, anio, serviceStatus);
                            carListItems.add(item);
                            allCarListItems.add(item);
                        }
                        carAdapter.notifyDataSetChanged();
                        if (carListItems.isEmpty()) {
                            Toast.makeText(getContext(), "No hay vehículos registrados en esta agencia.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e(TAG, "Error getting documents: ", task.getException());
                        Toast.makeText(getContext(), "Error al cargar vehículos.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void filterCars(String query) {
        List<CarListItem> filteredList = new ArrayList<>();
        if (query.isEmpty()) {
            filteredList.addAll(allCarListItems);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (CarListItem item : allCarListItems) {
                // Filtrar por placa o por ID del documento (Firebase ID)
                if (item.getPlaca().toLowerCase().contains(lowerCaseQuery) ||
                        item.getVehicleDocId().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(item);
                }
            }
        }
        carListItems.clear();
        carListItems.addAll(filteredList);
        carAdapter.notifyDataSetChanged();
    }

    @Override
    public void onCarClick(String vehicleDocId) {
        // Navegar al fragmento de detalles
        NavController navController = Navigation.findNavController(requireView());
        Bundle bundle = new Bundle();
        bundle.putString("vehicle_doc_id", vehicleDocId);
        navController.navigate(R.id.action_nav_car_list_to_nav_car_details, bundle);
    }

    // --- Clase interna para representar un ítem de la lista ---
    static class CarListItem {
        private String vehicleDocId; // ID del documento en Firebase (para pasar a detalles)
        private String placa;
        private String modelo;
        private int anio;
        private String serviceStatus; // "Servicio activo" o "Pago pendiente"

        public CarListItem(String vehicleDocId, String placa, String modelo, int anio, String serviceStatus) {
            this.vehicleDocId = vehicleDocId;
            this.placa = placa;
            this.modelo = modelo;
            this.anio = anio;
            this.serviceStatus = serviceStatus;
        }

        public String getVehicleDocId() { return vehicleDocId; }
        public String getPlaca() { return placa; }
        public String getModelo() { return modelo; }
        public int getAnio() { return anio; }
        public String getServiceStatus() { return serviceStatus; }
    }

    // --- Adaptador para el RecyclerView ---
    static class CarAdapter extends RecyclerView.Adapter<CarAdapter.CarViewHolder> {

        private List<CarListItem> carList;
        private OnCarClickListener listener;

        public CarAdapter(List<CarListItem> carList, OnCarClickListener listener) {
            this.carList = carList;
            this.listener = listener;
        }

        @NonNull
        @Override
        public CarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car_list, parent, false);
            return new CarViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CarViewHolder holder, int position) {
            CarListItem currentItem = carList.get(position);
            holder.tvPlaca.setText(currentItem.getPlaca());
            holder.tvModeloAnio.setText(currentItem.getModelo() + " " + currentItem.getAnio());
            holder.tvServiceStatus.setText(currentItem.getServiceStatus());

            // Ajustar el fondo del tag de estado
            if (currentItem.getServiceStatus().equals("Servicio Activo")) {
                holder.tvServiceStatus.setBackgroundResource(R.drawable.rounded_tag_active);
            } else {
                holder.tvServiceStatus.setBackgroundResource(R.drawable.rounded_tag_pending);
            }

            holder.itemView.setOnClickListener(v -> listener.onCarClick(currentItem.getVehicleDocId()));
        }

        @Override
        public int getItemCount() {
            return carList.size();
        }

        static class CarViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlaca;
            TextView tvModeloAnio;
            TextView tvServiceStatus;

            public CarViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPlaca = itemView.findViewById(R.id.tv_car_plate);
                tvModeloAnio = itemView.findViewById(R.id.tv_car_model_year);
                tvServiceStatus = itemView.findViewById(R.id.tv_service_status);
            }
        }
    }
}