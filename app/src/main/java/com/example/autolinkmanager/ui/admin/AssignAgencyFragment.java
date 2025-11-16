package com.example.autolinkmanager.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
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

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Lista de solicitudes
    private final List<RequestItem> requests = new ArrayList<>();
    private RequestAdapter adapter;

    // Clase interna para manejar las solicitudes
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

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        enforceAdminOrPop();

        adapter = new RequestAdapter();
        lvRequests.setAdapter(adapter);

        loadPendingRequests();

        return v;
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

        db.collection("users")
                .whereEqualTo("isActive", false)
                .get()
                .addOnSuccessListener(snaps -> {
                    requests.clear();

                    if (snaps.isEmpty()) {
                        progress.setVisibility(View.GONE);
                        tvNoRequests.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                        return;
                    }

                    // Cargar datos de cada usuario y su agencia solicitada
                    for (DocumentSnapshot d : snaps) {
                        String uid = d.getString("uid");
                        if (TextUtils.isEmpty(uid)) uid = d.getId();

                        String nombre = d.getString("nombre");
                        String email = d.getString("email");
                        String requestedAgencyId = d.getString("requestedAgencyId");

                        if (!TextUtils.isEmpty(requestedAgencyId)) {
                            // Obtener nombre de la agencia
                            String finalUid = uid;
                            String finalNombre = nombre != null ? nombre : "(Sin nombre)";
                            String finalEmail = email != null ? email : "";

                            db.collection("agencies").document(requestedAgencyId)
                                    .get()
                                    .addOnSuccessListener(agencyDoc -> {
                                        String agencyName = agencyDoc.getString("name");
                                        if (agencyName == null) agencyName = "(Agencia sin nombre)";

                                        requests.add(new RequestItem(
                                                finalUid,
                                                finalNombre,
                                                finalEmail,
                                                requestedAgencyId,
                                                agencyName
                                        ));

                                        adapter.notifyDataSetChanged();
                                        progress.setVisibility(View.GONE);
                                    })
                                    .addOnFailureListener(e -> {
                                        // Agregar aunque no se encuentre la agencia
                                        requests.add(new RequestItem(
                                                finalUid,
                                                finalNombre,
                                                finalEmail,
                                                requestedAgencyId,
                                                "(Agencia no encontrada)"
                                        ));

                                        adapter.notifyDataSetChanged();
                                        progress.setVisibility(View.GONE);
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error cargando solicitudes: " + e.getMessage());
                });
    }

    private void acceptRequest(RequestItem request) {
        progress.setVisibility(View.VISIBLE);

        // 1. Actualizar usuario: isActive=true, role=agency, agencyId
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("isActive", true); // Tu cambio a Booleano
        userUpdates.put("role", "agency");
        userUpdates.put("agencyId", request.agencyId);

        db.collection("users").document(request.userId)
                .update(userUpdates)
                .addOnSuccessListener(unused -> {
                    // 2. Actualizar agencia: userID con el uid del usuario
                    db.collection("agencies").document(request.agencyId)
                            .update("userID", request.userId)
                            .addOnSuccessListener(unused2 -> {
                                // 3. ¡NUEVO! Rechazar y eliminar a otros usuarios
                                //    que solicitaron la MISMA agencia.
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

        // Buscamos usuarios:
        // 1. Pendientes (isActive == false)
        // 2. Que pidieron la misma agencia (requestedAgencyId == agencyId)
        db.collection("users")
                .whereEqualTo("isActive", false) // Asumiendo 'false' es el estado pendiente
                .whereEqualTo("requestedAgencyId", agencyId)
                .get()
                .addOnSuccessListener(snaps -> {

                    if (snaps.isEmpty()) {
                        // No había otros usuarios que rechazar, terminamos.
                        progress.setVisibility(View.GONE);
                        toast("✅ Solicitud aceptada: " + acceptedUserName);
                        loadPendingRequests(); // Recargar lista
                        return;
                    }

                    // Usamos un WriteBatch para eliminar a todos los
                    // usuarios pendientes en una sola operación atómica.
                    WriteBatch batch = db.batch();
                    int rejectedCount = 0;

                    for (DocumentSnapshot doc : snaps) {
                        // Nos aseguramos de NO borrar al usuario que
                        // acabamos de aceptar (aunque no debería salir
                        // en esta query, es una buena precaución).
                        if (!doc.getId().equals(acceptedUserId)) {
                            batch.delete(doc.getReference());
                            rejectedCount++;
                        }
                    }

                    // Ejecutar la eliminación en lote
                    int finalRejectedCount = rejectedCount;
                    batch.commit()
                            .addOnSuccessListener(unused -> {
                                progress.setVisibility(View.GONE);
                                // Mensaje final
                                String toastMsg = "✅ Solicitud aceptada: " + acceptedUserName;
                                if (finalRejectedCount > 0) {
                                    toastMsg += ". " + finalRejectedCount + " otra(s) solicitud(es) fueron eliminadas.";
                                }
                                toast(toastMsg);
                                loadPendingRequests(); // Recargar lista
                            })
                            .addOnFailureListener(e -> {
                                progress.setVisibility(View.GONE);
                                toast("Error al eliminar otros usuarios: " + e.getMessage());
                                loadPendingRequests(); // Recargar de todos modos
                            });
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error buscando otros pendientes: " + e.getMessage());
                    loadPendingRequests(); // Recargar de todos modos
                });
    }

    private void rejectRequest(RequestItem request) {
        progress.setVisibility(View.VISIBLE);

        // Eliminar el documento del usuario en lugar de actualizarlo
        db.collection("users").document(request.userId)
                .delete() // <--- CAMBIO AQUÍ
                .addOnSuccessListener(unused -> {
                    progress.setVisibility(View.GONE);
                    // Mensaje actualizado para reflejar la eliminación
                    toast("❌ Usuario rechazado y eliminado: " + request.userName);

                    // Recargar lista
                    loadPendingRequests();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    // Mensaje de error actualizado
                    toast("Error al eliminar: " + e.getMessage());
                });
    }

    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }

    // Adapter personalizado para mostrar las solicitudes
    private class RequestAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return requests.size();
        }

        @Override
        public RequestItem getItem(int position) {
            return requests.get(position);
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