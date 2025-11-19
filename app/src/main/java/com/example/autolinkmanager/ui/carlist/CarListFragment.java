package com.example.autolinkmanager.ui.carlist;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autolinkmanager.R;
import com.example.autolinkmanager.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// INTERFAZ (Asegúrate que esté definida, ya sea aquí o en su archivo)
interface CarItemActionsListener {
    void onDetailsClick(String vehicleDocId);
    void onDeleteClick(String vehicleDocId, String placa);
    void onFinishServiceClick(String vehicleDocId);
}

public class CarListFragment extends Fragment implements CarItemActionsListener {

    private static final String TAG = "CarListFragment";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentAgencyId;

    private RecyclerView recyclerView;
    private CarAdapter carAdapter;

    // Listas de datos
    private List<CarListItem> carListItems;     // Lista visible (filtrada)
    private List<CarListItem> allCarListItems;  // Lista maestra (todos los datos)

    // Componentes UI
    private SearchView searchView;
    private Spinner spinnerTime, spinnerFinished, spinnerPayment;

    // Variables para Filtros
    private String currentQuery = "";
    private int filterTimeIndex = 0;     // 0: Todos, 1: Hoy, 2: Esta Semana, 3: Este Mes
    private int filterFinishedIndex = 0; // 0: Todos, 1: Finalizados, 2: Pendientes
    private int filterPaymentIndex = 0;  // 0: Todos, 1: Pagados, 2: No Pagados

    // Cámara
    private ActivityResultLauncher<Intent> cameraLauncher;
    private String currentProcessingVehicleId;

    public CarListFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        carListItems = new ArrayList<>();
        allCarListItems = new ArrayList<>();

        // Configuración de la cámara (igual que antes)
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == -1 && result.getData() != null) { // -1 es RESULT_OK
                        Bundle extras = result.getData().getExtras();
                        Bitmap imageBitmap = (Bitmap) extras.get("data");
                        if (imageBitmap != null && currentProcessingVehicleId != null) {
                            String base64Image = bitmapToBase64(imageBitmap);
                            completeServiceInFirestore(currentProcessingVehicleId, base64Image);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_car_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Inicializar RecyclerView
        recyclerView = view.findViewById(R.id.recycler_view_car_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        carAdapter = new CarAdapter(carListItems, this);
        recyclerView.setAdapter(carAdapter);

        // 2. Inicializar Buscador
        searchView = view.findViewById(R.id.search_view_car_list);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query;
                applyFilters();
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText;
                applyFilters();
                return true;
            }
        });

        // 3. Inicializar Spinners de Filtros
        setupSpinners(view);

        // 4. Cargar Datos
        loadUserAgencyId();
    }

    private void setupSpinners(View view) {
        spinnerTime = view.findViewById(R.id.spinner_filter_time);
        spinnerFinished = view.findViewById(R.id.spinner_filter_finished);
        spinnerPayment = view.findViewById(R.id.spinner_filter_payment);

        // Adaptadores para los Spinners
        ArrayAdapter<String> adapterTime = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item,
                new String[]{"Fecha: Todos", "Hoy", "Esta Semana", "Este Mes"});
        adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTime.setAdapter(adapterTime);

        ArrayAdapter<String> adapterFinished = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item,
                new String[]{"Estado: Todos", "Finalizados", "En Proceso"});
        adapterFinished.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFinished.setAdapter(adapterFinished);

        ArrayAdapter<String> adapterPayment = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item,
                new String[]{"Pago: Todos", "Pagados", "Pendientes"});
        adapterPayment.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPayment.setAdapter(adapterPayment);

        // Listeners
        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Actualizar índices
                if (parent == spinnerTime) filterTimeIndex = position;
                if (parent == spinnerFinished) filterFinishedIndex = position;
                if (parent == spinnerPayment) filterPaymentIndex = position;

                // Aplicar filtros
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerTime.setOnItemSelectedListener(filterListener);
        spinnerFinished.setOnItemSelectedListener(filterListener);
        spinnerPayment.setOnItemSelectedListener(filterListener);
    }

    private void loadUserAgencyId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            User user = task.getResult().toObject(User.class);
                            if (user != null && user.getAgencyId() != null) {
                                currentAgencyId = user.getAgencyId();
                                loadCars();
                            }
                        }
                    });
        }
    }

    private void loadCars() {
        if (currentAgencyId == null) return;

        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles")
                .orderBy("fechaIngreso", Query.Direction.DESCENDING) // Ordenar por fecha
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<CarListItem> newAllItems = new ArrayList<>();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String placa = document.getString("placa");
                            String modelo = document.getString("modelo");
                            Long anioLong = document.getLong("anio");
                            int anio = (anioLong != null) ? anioLong.intValue() : 0;

                            // Lectura segura de Booleanos
                            Boolean pagadoObj = document.getBoolean("pagado");
                            boolean pagado = (pagadoObj != null) && pagadoObj;

                            Boolean isFinishedObj = document.getBoolean("isFinished");
                            boolean isFinished = (isFinishedObj != null) && isFinishedObj;

                            // Lectura de fecha (puede venir como Timestamp de Firebase)
                            Date fechaIngreso = document.getDate("fechaIngreso"); // Firestore Timestamp -> Date

                            CarListItem item = new CarListItem(document.getId(), placa, modelo, anio, pagado, isFinished, fechaIngreso);
                            newAllItems.add(item);
                        }

                        allCarListItems.clear();
                        allCarListItems.addAll(newAllItems);

                        // Aplicar filtros inmediatamente después de cargar
                        applyFilters();
                    }
                });
    }

    // --- LÓGICA MAESTRA DE FILTRADO ---
    private void applyFilters() {
        List<CarListItem> tempFilteredList = new ArrayList<>();

        // Fechas para comparaciones
        Calendar cal = Calendar.getInstance();
        Date now = new Date();

        // Resetear horas para comparar fechas puras
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();

        cal.add(Calendar.DAY_OF_YEAR, -7);
        Date weekStart = cal.getTime();

        cal.setTime(todayStart); // Reset a hoy
        cal.add(Calendar.DAY_OF_YEAR, -30);
        Date monthStart = cal.getTime();

        for (CarListItem item : allCarListItems) {
            boolean matchesQuery = true;
            boolean matchesTime = true;
            boolean matchesFinished = true;
            boolean matchesPayment = true;

            // 1. Filtro de Texto (Buscador)
            if (currentQuery != null && !currentQuery.isEmpty()) {
                String q = currentQuery.toLowerCase();
                matchesQuery = (item.getPlaca() != null && item.getPlaca().toLowerCase().contains(q)) ||
                        (item.getModelo() != null && item.getModelo().toLowerCase().contains(q));
            }

            // 2. Filtro de Tiempo
            if (filterTimeIndex > 0 && item.getFechaIngreso() != null) {
                if (filterTimeIndex == 1) { // Hoy
                    matchesTime = !item.getFechaIngreso().before(todayStart);
                } else if (filterTimeIndex == 2) { // Semana (últimos 7 días)
                    matchesTime = !item.getFechaIngreso().before(weekStart);
                } else if (filterTimeIndex == 3) { // Mes (últimos 30 días)
                    matchesTime = !item.getFechaIngreso().before(monthStart);
                }
            } else if (filterTimeIndex > 0 && item.getFechaIngreso() == null) {
                matchesTime = false; // Si filtra por fecha y el auto no tiene fecha, no mostrar
            }

            // 3. Filtro de Finalizado
            if (filterFinishedIndex == 1) { // Solo Finalizados
                matchesFinished = item.isFinished();
            } else if (filterFinishedIndex == 2) { // Solo En Proceso (No finalizados)
                matchesFinished = !item.isFinished();
            }

            // 4. Filtro de Pago
            if (filterPaymentIndex == 1) { // Solo Pagados
                matchesPayment = item.isPagado();
            } else if (filterPaymentIndex == 2) { // Solo Pendientes (No pagados)
                matchesPayment = !item.isPagado();
            }

            // SI CUMPLE CON TODOS LOS FILTROS
            if (matchesQuery && matchesTime && matchesFinished && matchesPayment) {
                tempFilteredList.add(item);
            }
        }

        // Actualizar Adapter
        carListItems.clear();
        carListItems.addAll(tempFilteredList);
        carAdapter.notifyDataSetChanged();
    }


    // Métodos de la Interfaz (Acciones)
    @Override
    public void onDetailsClick(String vehicleDocId) {
        Bundle bundle = new Bundle();
        bundle.putString("vehicle_doc_id", vehicleDocId);
        Navigation.findNavController(requireView()).navigate(R.id.action_nav_car_list_to_nav_car_details, bundle);
    }

    @Override
    public void onDeleteClick(String vehicleDocId, String placa) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar")
                .setMessage("¿Eliminar " + placa + "?")
                .setPositiveButton("Sí", (d, w) -> deleteVehicle(vehicleDocId))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteVehicle(String docId) {
        if (currentAgencyId != null) {
            db.collection("agencies").document(currentAgencyId)
                    .collection("vehicles").document(docId)
                    .delete()
                    .addOnSuccessListener(v -> loadCars());
        }
    }

    @Override
    public void onFinishServiceClick(String vehicleDocId) {
        this.currentProcessingVehicleId = vehicleDocId;
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            cameraLauncher.launch(takePictureIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), "Error cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private void completeServiceInFirestore(String vehicleDocId, String base64Image) {
        if (currentAgencyId == null || vehicleDocId == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("isFinished", true);
        updates.put("pagado", true);
        updates.put("fotoTerminadoBase64", base64Image);

        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles").document(vehicleDocId)
                .update(updates)
                .addOnSuccessListener(a -> {
                    Toast.makeText(getContext(), "Servicio Finalizado", Toast.LENGTH_SHORT).show();
                    loadCars();
                });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
    }

    // --- CLASES INTERNAS ---

    // Modelo de Datos (Actualizado con Date y Booleans)
    static class CarListItem {
        private String vehicleDocId;
        private String placa;
        private String modelo;
        private int anio;
        private boolean pagado;
        private boolean isFinished;
        private Date fechaIngreso;

        public CarListItem(String id, String p, String m, int a, boolean pag, boolean fin, Date fecha) {
            this.vehicleDocId = id; this.placa = p; this.modelo = m; this.anio = a;
            this.pagado = pag; this.isFinished = fin; this.fechaIngreso = fecha;
        }
        // Getters
        public String getVehicleDocId() { return vehicleDocId; }
        public String getPlaca() { return placa; }
        public String getModelo() { return modelo; }
        public int getAnio() { return anio; }
        public boolean isPagado() { return pagado; }
        public boolean isFinished() { return isFinished; }
        public Date getFechaIngreso() { return fechaIngreso; }
    }

    // Adapter
    static class CarAdapter extends RecyclerView.Adapter<CarAdapter.CarViewHolder> {
        private List<CarListItem> list;
        private CarItemActionsListener listener;

        public CarAdapter(List<CarListItem> list, CarItemActionsListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public CarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car_list, parent, false);
            return new CarViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CarViewHolder holder, int position) {
            CarListItem item = list.get(position);
            holder.tvPlaca.setText(item.getPlaca());
            holder.tvModelo.setText(item.getModelo() + " " + item.getAnio());

            // Configurar etiqueta de PAGO
            if (item.isPagado()) {
                holder.tvStatus.setText("Pagado");
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_tag_active); // Verde o similar
            } else {
                holder.tvStatus.setText("Pendiente Pago");
                holder.tvStatus.setBackgroundResource(R.drawable.rounded_tag_pending); // Rojo o similar
            }

            // Configurar etiqueta de FINALIZADO (Nueva)
            if (item.isFinished()) {
                holder.tvFinished.setText("Terminado");
                holder.tvFinished.setTextColor(Color.GREEN);
                // Puedes cambiar el background si tienes un drawable para esto
                // holder.tvFinished.setBackgroundResource(...);
            } else {
                holder.tvFinished.setText("En Proceso");
                holder.tvFinished.setTextColor(Color.parseColor("#FFA500")); // Naranja
            }

            // Menu Click
            holder.btnMenu.setOnClickListener(v -> showPopupMenu(v, item));
        }

        @Override
        public int getItemCount() { return list.size(); }

        private void showPopupMenu(View view, CarListItem item) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.car_item_menu, popup.getMenu());

            // Ocultar opción Terminar si ya está finalizado
            MenuItem finishItem = popup.getMenu().findItem(R.id.action_finish_service);
            if (finishItem != null) finishItem.setVisible(!item.isFinished());

            popup.setOnMenuItemClickListener(menuItem -> {
                int id = menuItem.getItemId();
                if (id == R.id.action_details) listener.onDetailsClick(item.getVehicleDocId());
                else if (id == R.id.action_delete) listener.onDeleteClick(item.getVehicleDocId(), item.getPlaca());
                else if (id == R.id.action_finish_service) listener.onFinishServiceClick(item.getVehicleDocId());
                return true;
            });
            popup.show();
        }

        static class CarViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlaca, tvModelo, tvStatus, tvFinished;
            ImageButton btnMenu;
            public CarViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPlaca = itemView.findViewById(R.id.tv_car_plate);
                tvModelo = itemView.findViewById(R.id.tv_car_model_year);
                tvStatus = itemView.findViewById(R.id.tv_service_status);
                tvFinished = itemView.findViewById(R.id.tv_finished_status); // Nuevo ID
                btnMenu = itemView.findViewById(R.id.btn_car_menu);
            }
        }
    }
}