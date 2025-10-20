package com.example.autolinkmanager.ui.calendar;

import androidx.core.content.ContextCompat;
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
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autolinkmanager.R;
import com.example.autolinkmanager.User;
import com.google.android.material.datepicker.DayViewDecorator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

interface OnCalendarEventClickListener {
    void onEventClick(CalendarFragment.CalendarEvent event);
}

// ***** IMPLEMENTA OnDateChangeListener (del CalendarView nativo) *****
public class CalendarFragment extends Fragment implements CalendarView.OnDateChangeListener, OnCalendarEventClickListener {

    private static final String TAG = "CalendarFragmentDebug";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentAgencyId;

    // UI
    // ***** USA CalendarView NATIVO *****
    private CalendarView calendarView;
    private RecyclerView recyclerViewEvents;
    private TextView tvSelectedDate;
    private TextView tvNoEvents;
    private FloatingActionButton fabAddEvent;

    // Data
    private List<CalendarEvent> allEvents;
    private CalendarEventAdapter eventAdapter;
    private List<CalendarEvent> eventsForSelectedDay;
    private SimpleDateFormat selectedDateFormatter;
    private Calendar selectedCalendar; // Para guardar la fecha seleccionada

    public CalendarFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        allEvents = new ArrayList<>();
        eventsForSelectedDay = new ArrayList<>();
        selectedDateFormatter = new SimpleDateFormat("EEEE, d MMM.", new Locale("es", "ES"));
        selectedCalendar = Calendar.getInstance(); // Inicializa con hoy
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Usa el layout que tiene <CalendarView>
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView = view.findViewById(R.id.calendarView);
        recyclerViewEvents = view.findViewById(R.id.recycler_view_calendar_events);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);
        tvNoEvents = view.findViewById(R.id.tv_no_events);
        fabAddEvent = view.findViewById(R.id.fab_add_event);

        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        // Asegúrate que CalendarEventAdapter exista y use CalendarEvent
        eventAdapter = new CalendarEventAdapter(eventsForSelectedDay, this);
        recyclerViewEvents.setAdapter(eventAdapter);

        // ***** USA setOnDateChangeListener *****
        calendarView.setOnDateChangeListener(this);

        fabAddEvent.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(v);
            navController.navigate(R.id.action_nav_calendar_to_nav_add_car);
        });

        loadUserAgencyId(); // Inicia la carga de datos
        updateSelectedDateLabel(selectedCalendar.getTime()); // Muestra la fecha de hoy
    }

    private void loadUserAgencyId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            Log.d(TAG, "Cargando AgencyID para UID: " + uid);
            db.collection("users").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document != null && document.exists()) {
                                User user = document.toObject(User.class);
                                if (user != null && user.getAgencyId() != null) {
                                    currentAgencyId = user.getAgencyId();
                                    Log.d(TAG, "AgencyID cargado: " + currentAgencyId);
                                    loadAllEvents(); // Llama a cargar eventos AQUI
                                } else {
                                    Log.w(TAG, "Usuario encontrado pero sin AgencyID.");
                                }
                            } else {
                                Log.w(TAG, "Documento de usuario no encontrado.");
                            }
                        } else {
                            Log.e(TAG, "Error al cargar usuario", task.getException());
                        }
                    });
        } else {
            Log.w(TAG, "Usuario no autenticado.");
        }
    }

    // Carga TODOS los eventos pero ya NO decora el calendario
    private void loadAllEvents() {
        if (currentAgencyId == null) {
            Log.e(TAG, "currentAgencyId es null, no se pueden cargar eventos.");
            return;
        }
        Log.d(TAG, "Iniciando carga de eventos para agencia: " + currentAgencyId);

        allEvents.clear();
        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Log.d(TAG, "Eventos leídos de Firebase: " + task.getResult().size() + " documentos.");

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String vehicleDocId = document.getId();
                            String placa = document.getString("placa");
                            String modelo = document.getString("modelo");

                            Timestamp ingresoTimestamp = null;
                            Timestamp salidaTimestamp = null;
                            try {
                                ingresoTimestamp = document.getTimestamp("fechaIngreso");
                                salidaTimestamp = document.getTimestamp("fechaSalida");
                            } catch (Exception e) {
                                Log.e(TAG, "Error al leer Timestamp para doc ID " + vehicleDocId, e);
                            }

                            Log.d(TAG, "Doc ID: " + vehicleDocId + ", Placa: " + placa + ", IngresoTS: " + ingresoTimestamp + ", SalidaTS: " + salidaTimestamp);

                            if (ingresoTimestamp != null) {
                                // ***** Usa Date directamente *****
                                allEvents.add(new CalendarEvent(ingresoTimestamp.toDate(), "Ingreso", vehicleDocId, placa, modelo));
                                Log.d(TAG, "  -> Evento Ingreso creado");
                            }
                            if (salidaTimestamp != null) {
                                // ***** Usa Date directamente *****
                                allEvents.add(new CalendarEvent(salidaTimestamp.toDate(), "Salida", vehicleDocId, placa, modelo));
                                Log.d(TAG, "  -> Evento Salida creado");
                            }
                        }

                        Log.d(TAG, "Total eventos creados: " + allEvents.size());
                        // ***** YA NO SE LLAMA A addEventDecorators *****

                        // Mostrar eventos para la fecha seleccionada actualmente (hoy)
                        filterEventsForSelectedDate(selectedCalendar);

                    } else {
                        Log.e(TAG, "Error getting events: ", task.getException());
                        Toast.makeText(getContext(), "Error al cargar eventos.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Actualiza la etiqueta de la fecha seleccionada
    private void updateSelectedDateLabel(Date date) {
        if (date != null) {
            tvSelectedDate.setText(selectedDateFormatter.format(date));
        } else {
            tvSelectedDate.setText("");
        }
    }

    // Filtra y muestra los eventos para la fecha dada
    private void filterEventsForSelectedDate(Calendar selectedDateCal) {
        eventsForSelectedDay.clear();
        // Normalizar la fecha seleccionada a medianoche
        selectedDateCal.set(Calendar.HOUR_OF_DAY, 0);
        selectedDateCal.set(Calendar.MINUTE, 0);
        selectedDateCal.set(Calendar.SECOND, 0);
        selectedDateCal.set(Calendar.MILLISECOND, 0);
        long selectedMillis = selectedDateCal.getTimeInMillis();

        Calendar eventCal = Calendar.getInstance();
        for (CalendarEvent event : allEvents) {
            if (event.getDate() == null) continue; // Saltar si la fecha del evento es nula

            eventCal.setTime(event.getDate());
            // Normalizar fecha del evento
            eventCal.set(Calendar.HOUR_OF_DAY, 0);
            eventCal.set(Calendar.MINUTE, 0);
            eventCal.set(Calendar.SECOND, 0);
            eventCal.set(Calendar.MILLISECOND, 0);

            // Comparar si son el mismo día
            if (eventCal.getTimeInMillis() == selectedMillis) {
                eventsForSelectedDay.add(event);
            }
        }

        Log.d(TAG,"Eventos encontrados para " + selectedDateFormatter.format(selectedDateCal.getTime()) + ": " + eventsForSelectedDay.size());

        // Actualizar UI
        if (eventsForSelectedDay.isEmpty()) {
            recyclerViewEvents.setVisibility(View.GONE);
            tvNoEvents.setVisibility(View.VISIBLE);
        } else {
            recyclerViewEvents.setVisibility(View.VISIBLE);
            tvNoEvents.setVisibility(View.GONE);
        }
        // Asegúrate que eventAdapter no sea null
        if (eventAdapter != null) {
            eventAdapter.notifyDataSetChanged();
        } else {
            Log.e(TAG, "eventAdapter es null al intentar notificar cambios.");
        }
    }


    // --- OnDateChangeListener (del CalendarView nativo) ---
    @Override
    public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
        // month es 0-indexado (Enero=0)
        Log.d(TAG, "Fecha seleccionada (Y/M/D): " + year + "/" + month + "/" + dayOfMonth);
        selectedCalendar.set(year, month, dayOfMonth);
        updateSelectedDateLabel(selectedCalendar.getTime());
        filterEventsForSelectedDate(selectedCalendar);
    }

    // --- OnCalendarEventClickListener (del RecyclerView) ---
    @Override
    public void onEventClick(CalendarEvent event) {
        Log.d(TAG, "Clic en evento: " + event.getEventType() + " - " + event.getVehicleDocId());
        NavController navController = Navigation.findNavController(requireView());
        Bundle bundle = new Bundle();
        bundle.putString("vehicle_doc_id", event.getVehicleDocId());
        // Asegúrate que esta acción exista en tu mobile_navigation.xml
        navController.navigate(R.id.action_nav_calendar_to_nav_car_details, bundle);
    }

    // --- Clase interna para los datos de un evento (usa Date) ---
    static class CalendarEvent {
        private Date date; // Usa Date estándar de Java
        private String eventType;
        private String vehicleDocId;
        private String placa;
        private String modelo;

        public CalendarEvent(Date date, String eventType, String vehicleDocId, String placa, String modelo) {
            this.date = date;
            this.eventType = eventType;
            this.vehicleDocId = vehicleDocId;
            this.placa = placa;
            this.modelo = modelo;
        }

        // Getters
        public Date getDate() { return date; }
        public String getEventType() { return eventType; }
        public String getVehicleDocId() { return vehicleDocId; }
        public String getPlaca() { return placa; }
        public String getModelo() { return modelo; }
    }

    // ***** YA NO SE NECESITA LA CLASE EventDecorator *****
}