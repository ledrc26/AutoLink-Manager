package com.example.autolinkmanager.ui.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

    private ListView lvRequests;
    private ProgressBar progress;
    private TextView tvNoRequests;
    private EditText etSearch; // 1. Declarar el buscador

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Lista "Maestra" (contiene todos los datos originales)
    private final List<RequestItem> allRequests = new ArrayList<>();
    // Lista "Visible" (contiene solo lo filtrado para el Adapter)
    private final List<RequestItem> displayedRequests = new ArrayList<>();

    private RequestAdapter adapter;

    public static class RequestItem {
        public String userId;
        public String userName;
        public String userEmail;
        public String agencyId;
        public String agencyName;

        public RequestItem(String userId, String userName, String userEmail, String agencyId, String agencyName) {
            this.userId = userId;
            this.userName = userName;
            this.userEmail = userEmail;
            this.agencyId = agencyId;
            this.agencyName = agencyName;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_assign_agency, container, false);

        lvRequests = v.findViewById(R.id.lvRequests);
        progress = v.findViewById(R.id.progress);
        tvNoRequests = v.findViewById(R.id.tvNoRequests);
        etSearch = v.findViewById(R.id.etSearch); // 2. Vincular vista

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        enforceAdminOrPop();

        adapter = new RequestAdapter();
        lvRequests.setAdapter(adapter);

        // 3. Configurar el listener del buscador
        setupSearchFilter();

        loadPendingRequests();

        return v;
    }

    // Lógica del buscador
    private void setupSearchFilter() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // Método para filtrar
    private void filterList(String text) {
        displayedRequests.clear();

        if (TextUtils.isEmpty(text)) {
            // Si no hay texto, mostramos todo
            displayedRequests.addAll(allRequests);
        } else {
            String query = text.toLowerCase();
            for (RequestItem item : allRequests) {
                // Buscar por nombre O por email
                if (item.userName.toLowerCase().contains(query) ||
                        item.userEmail.toLowerCase().contains(query)) {
                    displayedRequests.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();

        // Controlar visibilidad del mensaje "No hay solicitudes" si el filtro no retorna nada
        if (displayedRequests.isEmpty() && !allRequests.isEmpty()) {
            // Opcional: Podrías poner un texto diferente como "No hay resultados para la búsqueda"
            // Pero por ahora usaremos el mismo TextView o lo ocultamos
            tvNoRequests.setVisibility(View.GONE);
        } else if (displayedRequests.isEmpty() && allRequests.isEmpty()) {
            tvNoRequests.setVisibility(View.VISIBLE);
        } else {
            tvNoRequests.setVisibility(View.GONE);
        }
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

    private void loadPendingRequests() {
        progress.setVisibility(View.VISIBLE);
        tvNoRequests.setVisibility(View.GONE);

        // Limpiamos ambas listas antes de cargar
        allRequests.clear();
        displayedRequests.clear();
        adapter.notifyDataSetChanged();

        db.collection("users")
                .whereEqualTo("isActive", false)
                .get()
                .addOnSuccessListener(snaps -> {

                    if (snaps.isEmpty()) {
                        progress.setVisibility(View.GONE);
                        tvNoRequests.setVisibility(View.VISIBLE);
                        return; // Salimos si no hay nada
                    }

                    // Variable atómica para contar procesos asíncronos completados
                    // (Necesario porque buscamos el nombre de agencia uno por uno)
                    final int totalDocs = snaps.size();
                    final int[] loadedDocs = {0};

                    for (DocumentSnapshot d : snaps) {
                        String uid = d.getString("uid");
                        if (TextUtils.isEmpty(uid)) uid = d.getId();

                        String nombre = d.getString("nombre");
                        String email = d.getString("email");
                        String requestedAgencyId = d.getString("requestedAgencyId");

                        String finalUid = uid;
                        String finalNombre = nombre != null ? nombre : "(Sin nombre)";
                        String finalEmail = email != null ? email : "";

                        if (!TextUtils.isEmpty(requestedAgencyId)) {
                            db.collection("agencies").document(requestedAgencyId)
                                    .get()
                                    .addOnSuccessListener(agencyDoc -> {
                                        String agencyName = agencyDoc.getString("name");
                                        if (agencyName == null) agencyName = "(Agencia sin nombre)";

                                        addRequestToList(new RequestItem(finalUid, finalNombre, finalEmail, requestedAgencyId, agencyName));
                                        checkIfFinished(++loadedDocs[0], totalDocs);
                                    })
                                    .addOnFailureListener(e -> {
                                        addRequestToList(new RequestItem(finalUid, finalNombre, finalEmail, requestedAgencyId, "(Agencia no encontrada)"));
                                        checkIfFinished(++loadedDocs[0], totalDocs);
                                    });
                        } else {
                            // Si no tiene agencia solicitada, contamos como procesado pero quizás no lo agregamos o lo agregamos con N/A
                            checkIfFinished(++loadedDocs[0], totalDocs);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error cargando solicitudes: " + e.getMessage());
                });
    }

    // Método auxiliar para agregar a las listas
    private void addRequestToList(RequestItem item) {
        allRequests.add(item);
        // Aplicar filtro actual si el usuario ya escribió algo mientras cargaba
        String currentSearch = etSearch.getText().toString();
        if (TextUtils.isEmpty(currentSearch)) {
            displayedRequests.add(item);
        } else {
            if (item.userName.toLowerCase().contains(currentSearch.toLowerCase()) ||
                    item.userEmail.toLowerCase().contains(currentSearch.toLowerCase())) {
                displayedRequests.add(item);
            }
        }
    }

    // Método auxiliar para checar si terminaron las cargas asíncronas
    private void checkIfFinished(int current, int total) {
        if (current >= total) {
            progress.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            if (displayedRequests.isEmpty() && allRequests.isEmpty()) {
                tvNoRequests.setVisibility(View.VISIBLE);
            }
        }
    }

    private void acceptRequest(RequestItem request) {
        progress.setVisibility(View.VISIBLE);

        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("isActive", true);
        userUpdates.put("role", "agency");
        userUpdates.put("agencyId", request.agencyId);

        db.collection("users").document(request.userId)
                .update(userUpdates)
                .addOnSuccessListener(unused -> {
                    db.collection("agencies").document(request.agencyId)
                            .update("userID", request.userId)
                            .addOnSuccessListener(unused2 -> {
                                findAndRejectOthers(request.agencyId, request.userId, request.userName);
                            })
                            .addOnFailureListener(e -> {
                                progress.setVisibility(View.GONE);
                                toast("Error al actualizar agencia: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error al aceptar: " + e.getMessage());
                });
    }

    private void findAndRejectOthers(String agencyId, String acceptedUserId, String acceptedUserName) {
        db.collection("users")
                .whereEqualTo("isActive", false)
                .whereEqualTo("requestedAgencyId", agencyId)
                .get()
                .addOnSuccessListener(snaps -> {
                    if (snaps.isEmpty()) {
                        progress.setVisibility(View.GONE);
                        toast("✅ Solicitud aceptada: " + acceptedUserName);
                        loadPendingRequests();
                        return;
                    }

                    WriteBatch batch = db.batch();
                    int rejectedCount = 0;

                    for (DocumentSnapshot doc : snaps) {
                        if (!doc.getId().equals(acceptedUserId)) {
                            batch.delete(doc.getReference());
                            rejectedCount++;
                        }
                    }

                    int finalRejectedCount = rejectedCount;
                    batch.commit()
                            .addOnSuccessListener(unused -> {
                                progress.setVisibility(View.GONE);
                                String toastMsg = "✅ Solicitud aceptada: " + acceptedUserName;
                                if (finalRejectedCount > 0) {
                                    toastMsg += ". " + finalRejectedCount + " otra(s) solicitud(es) fueron eliminadas.";
                                }
                                toast(toastMsg);
                                loadPendingRequests();
                            })
                            .addOnFailureListener(e -> {
                                progress.setVisibility(View.GONE);
                                toast("Error al eliminar otros usuarios: " + e.getMessage());
                                loadPendingRequests();
                            });
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error buscando otros pendientes: " + e.getMessage());
                    loadPendingRequests();
                });
    }

    private void rejectRequest(RequestItem request) {
        progress.setVisibility(View.VISIBLE);
        db.collection("users").document(request.userId)
                .delete()
                .addOnSuccessListener(unused -> {
                    progress.setVisibility(View.GONE);
                    toast("❌ Usuario rechazado y eliminado: " + request.userName);
                    loadPendingRequests();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error al eliminar: " + e.getMessage());
                });
    }

    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }

    private class RequestAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            // 4. El adaptador ahora usa displayedRequests
            return displayedRequests.size();
        }

        @Override
        public RequestItem getItem(int position) {
            return displayedRequests.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_request, parent, false);
            }

            RequestItem item = getItem(position);

            TextView tvUserName = convertView.findViewById(R.id.tvUserName);
            TextView tvUserEmail = convertView.findViewById(R.id.tvUserEmail);
            TextView tvAgencyName = convertView.findViewById(R.id.tvAgencyName);
            Button btnAccept = convertView.findViewById(R.id.btnAccept);
            Button btnReject = convertView.findViewById(R.id.btnReject);

            tvUserName.setText(item.userName);
            tvUserEmail.setText(item.userEmail);
            tvAgencyName.setText("Solicita: " + item.agencyName);

            btnAccept.setOnClickListener(v -> acceptRequest(item));
            btnReject.setOnClickListener(v -> rejectRequest(item));

            return convertView;
        }
    }
}