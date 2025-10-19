package com.example.autolinkmanager.ui.admin;

import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.autolinkmanager.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.GeoPoint;

import java.util.List;

public class AgenciesAdapter extends RecyclerView.Adapter<AgenciesAdapter.VH> {

    public interface OnAgencyAction {
        void onEdit(DocumentSnapshot doc);
        void onDelete(DocumentSnapshot doc);
    }

    private List<DocumentSnapshot> data;
    private final OnAgencyAction listener;

    public AgenciesAdapter(List<DocumentSnapshot> data, OnAgencyAction listener) {
        this.data = data;
        this.listener = listener;
    }

    public void setData(List<DocumentSnapshot> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_agency, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DocumentSnapshot d = data.get(pos);
        String name = d.getString("name");
        String phone = d.getString("phone");
        GeoPoint gp = d.getGeoPoint("location");

        h.tvName.setText(name != null ? name : "(Sin nombre)");
        h.tvPhone.setText("Tel: " + (phone != null ? phone : "-"));
        if (gp != null) {
            h.tvCoords.setText(String.format("Ubicación: %.6f, %.6f", gp.getLatitude(), gp.getLongitude()));
        } else {
            h.tvCoords.setText("Ubicación: -");
        }

        h.btnEdit.setOnClickListener(v -> listener.onEdit(d));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(d));
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvPhone, tvCoords;
        Button btnEdit, btnDelete;
        VH(@NonNull View item) {
            super(item);
            tvName = item.findViewById(R.id.tvName);
            tvPhone = item.findViewById(R.id.tvPhone);
            tvCoords = item.findViewById(R.id.tvCoords);
            btnEdit = item.findViewById(R.id.btnEdit);
            btnDelete = item.findViewById(R.id.btnDelete);
        }
    }
}
