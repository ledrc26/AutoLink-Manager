package com.example.autolinkmanager.ui.calendar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.autolinkmanager.R;

import java.util.List;

public class CalendarEventAdapter extends RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder> {

    private List<CalendarFragment.CalendarEvent> eventList;
    private OnCalendarEventClickListener listener;

    public CalendarEventAdapter(List<CalendarFragment.CalendarEvent> eventList, OnCalendarEventClickListener listener) {
        this.eventList = eventList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        CalendarFragment.CalendarEvent currentEvent = eventList.get(position);

        holder.tvEventType.setText(currentEvent.getEventType());
        holder.tvCarInfo.setText(currentEvent.getPlaca() + " - " + currentEvent.getModelo());

        // Cambiar el color del punto
        if ("Ingreso".equals(currentEvent.getEventType())) {
            holder.dotView.setBackgroundResource(R.drawable.event_dot_ingreso);
        } else { // Asumimos "Salida"
            holder.dotView.setBackgroundResource(R.drawable.event_dot_salida);
        }

        // Establecer listener para el clic en el item
        holder.itemView.setOnClickListener(v -> listener.onEventClick(currentEvent));
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        View dotView;
        TextView tvEventType;
        TextView tvCarInfo;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            dotView = itemView.findViewById(R.id.view_event_dot);
            tvEventType = itemView.findViewById(R.id.tv_event_type);
            tvCarInfo = itemView.findViewById(R.id.tv_event_car_info);
        }
    }
}