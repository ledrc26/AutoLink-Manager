package com.example.autolinkmanager.ui.carlist;

import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
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

import android.widget.ImageButton;
import android.widget.PopupMenu;
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

interface CarItemActionsListener {
    void onDetailsClick(String vehicleDocId);
    void onDeleteClick(String vehicleDocId, String placa);
}

public class CarListFragment extends Fragment implements CarItemActionsListener {

    private static final String TAG = "CarListFragment";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentAgencyId;

    private RecyclerView recyclerView;
    private CarAdapter carAdapter;
    private List<CarListItem> carListItems; // La lista que ve el Adapter
    private List<CarListItem> allCarListItems; // La lista maestra con todos los carros
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

        // El Adapter se crea con la lista 'carListItems'.
        // Esta lista persistirá mientras el Fragmento exista (no solo su vista)
        carAdapter = new CarAdapter(carListItems, this);
        recyclerView.setAdapter(carAdapter);

        searchView = view.findViewById(R.id.search_view_car_list);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { filterCars(query); return true; }
            @Override
            public boolean onQueryTextChange(String newText) { filterCars(newText); return true; }
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
                                    loadCars();
                                } else { /* Handle missing agencyId */ }
                            } else { /* Handle missing user doc */ }
                        } else { /* Handle error loading user */ }
                    });
        } else { /* Handle not logged in */ }
    }

    // --- MÉTODO LOADCARS() CORREGIDO ---
    private void loadCars() {
        if (currentAgencyId == null) {
            Log.w(TAG, "currentAgencyId es nulo, no se pueden cargar autos.");
            return;
        }

        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles")
                .orderBy("placa", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    // Comprueba si la tarea fue exitosa Y si el resultado no es nulo
                    if (task.isSuccessful() && task.getResult() != null) {

                        // 1. Crea listas temporales para los nuevos datos
                        List<CarListItem> newItems = new ArrayList<>();
                        List<CarListItem> newAllItems = new ArrayList<>();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String placa = document.getString("placa");
                            String modelo = document.getString("modelo");
                            Long anioLong = document.getLong("anio");
                            int anio = (anioLong != null) ? anioLong.intValue() : 0;
                            Boolean pagado = document.getBoolean("pagado");
                            String serviceStatus = (pagado != null && pagado) ? "Pagado" : "Pendiente"; // Changed text

                            CarListItem item = new CarListItem(document.getId(), placa, modelo, anio, serviceStatus);
                            newItems.add(item);
                            newAllItems.add(item);
                        }

                        // 2. SOLO AHORA, borra las listas viejas
                        carListItems.clear();
                        allCarListItems.clear();

                        // 3. Agrega los nuevos datos
                        carListItems.addAll(newItems);
                        allCarListItems.addAll(newAllItems);

                        // 4. Notifica al adapter
                        carAdapter.notifyDataSetChanged();

                        // Opcional: Muestra mensaje si la lista (ahora llena) está vacía
                        if (newItems.isEmpty() && getContext() != null) {
                            Toast.makeText(getContext(), "No se encontraron vehículos.", Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        // 5. Maneja el error
                        Log.e(TAG, "Error al cargar vehículos: ", task.getException());
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Error al cargar vehículos.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // --- MÉTODO FILTERCARS() CORREGIDO ---
    private void filterCars(String query) {
        List<CarListItem> filteredList = new ArrayList<>();

        if (query == null || query.isEmpty()) {
            filteredList.addAll(allCarListItems); // Carga todo desde la lista maestra
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (CarListItem item : allCarListItems) { // Filtra desde la lista maestra
                if ((item.getPlaca() != null && item.getPlaca().toLowerCase().contains(lowerCaseQuery)) ||
                        (item.getVehicleDocId() != null && item.getVehicleDocId().toLowerCase().contains(lowerCaseQuery))) {
                    filteredList.add(item);
                }
            }
        }

        // Actualiza la lista que el adapter SÍ está viendo
        carListItems.clear();
        carListItems.addAll(filteredList);
        carAdapter.notifyDataSetChanged(); // Notifica al adapter del cambio
    }

    // --- Implementation of CarItemActionsListener ---

    @Override
    public void onDetailsClick(String vehicleDocId) {
        // Asegúrate de que la vista no es nula
        if (getView() == null) return;
        NavController navController = Navigation.findNavController(requireView());
        Bundle bundle = new Bundle();
        bundle.putString("vehicle_doc_id", vehicleDocId);
        // Use your correct action ID
        navController.navigate(R.id.action_nav_car_list_to_nav_car_details, bundle);
    }

    @Override
    public void onDeleteClick(String vehicleDocId, String placa) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirmar Eliminación")
                .setMessage("¿Seguro que deseas eliminar el vehículo " + placa + "?")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    deleteVehicleFromFirestore(vehicleDocId);
                })
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteVehicleFromFirestore(String vehicleDocId) {
        if (currentAgencyId == null || vehicleDocId == null) return;

        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles").document(vehicleDocId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Vehículo eliminado.", Toast.LENGTH_SHORT).show();
                    }
                    loadCars(); // Refresh the list
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Error al eliminar.", Toast.LENGTH_SHORT).show();
                    }
                    Log.e(TAG, "Error al eliminar vehículo: ", e);
                });
    }

    // --- Inner class CarListItem ---
    static class CarListItem {
        private String vehicleDocId;
        private String placa;
        private String modelo;
        private int anio;
        private String serviceStatus; // "Pagado" or "Pendiente"

        public CarListItem(String vehicleDocId, String placa, String modelo, int anio, String serviceStatus) {
            this.vehicleDocId = vehicleDocId;
            this.placa = placa;
            this.modelo = modelo;
            this.anio = anio;
            this.serviceStatus = serviceStatus;
        }

        // Getters...
        public String getVehicleDocId() { return vehicleDocId; }
        public String getPlaca() { return placa; }
        public String getModelo() { return modelo; }
        public int getAnio() { return anio; }
        public String getServiceStatus() { return serviceStatus; }
    }

    // --- Adapter CarAdapter (CORREGIDO) ---
    static class CarAdapter extends RecyclerView.Adapter<CarAdapter.CarViewHolder> {

        private List<CarListItem> carList;
        private CarItemActionsListener listener;

        public CarAdapter(List<CarListItem> carList, CarItemActionsListener listener) {
            this.carList = carList; // Usa la lista que se le pasa
            this.listener = listener;
        }

        // --- MÉTODO updateList() ELIMINADO ---
        // Ya no es necesario, el filtro actualiza la lista original.

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

            // Set status tag background
            if ("Pagado".equalsIgnoreCase(currentItem.getServiceStatus())) {
                holder.tvServiceStatus.setBackgroundResource(R.drawable.rounded_tag_active); // Green
            } else {
                holder.tvServiceStatus.setBackgroundResource(R.drawable.rounded_tag_pending); // Red
            }

            // Set up the popup menu for the 3-dot button
            holder.btnMenu.setOnClickListener(v -> showPopupMenu(v, currentItem));
        }

        @Override
        public int getItemCount() {
            return carList.size();
        }

        // Method to show the popup menu
        private void showPopupMenu(View view, CarListItem item) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.car_item_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(menuItem -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.action_details) {
                    listener.onDetailsClick(item.getVehicleDocId());
                    return true;
                } else if (itemId == R.id.action_delete) {
                    listener.onDeleteClick(item.getVehicleDocId(), item.getPlaca());
                    return true;
                } else {
                    return false;
                }
            });
            popup.show();
        }

        static class CarViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlaca;
            TextView tvModeloAnio;
            TextView tvServiceStatus;
            ImageButton btnMenu; // Reference to the 3-dot button

            public CarViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPlaca = itemView.findViewById(R.id.tv_car_plate);
                tvModeloAnio = itemView.findViewById(R.id.tv_car_model_year);
                tvServiceStatus = itemView.findViewById(R.id.tv_service_status);
                btnMenu = itemView.findViewById(R.id.btn_car_menu); // Find the button
            }
        }
    }
}