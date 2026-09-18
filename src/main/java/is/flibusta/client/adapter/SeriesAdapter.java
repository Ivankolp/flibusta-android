package is.flibusta.client.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.R;
import is.flibusta.client.data.Series;

import java.util.ArrayList;
import java.util.List;

public class SeriesAdapter extends RecyclerView.Adapter<SeriesAdapter.ViewHolder> {
    private final Context context;
    private final List<Series> seriesList;

    public interface OnSeriesClickListener {
        void onSeriesClick(Series series);
    }

    private OnSeriesClickListener listener;

    public SeriesAdapter(Context context, List<Series> seriesList) {
        this.context = context;
        this.seriesList = seriesList != null ? seriesList : new ArrayList<>();
    }

    public void setListener(OnSeriesClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<Series> newList) {
        this.seriesList.clear();
        if (newList != null) {
            this.seriesList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_series, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Series series = seriesList.get(position);

        holder.tvTitle.setText(series.getTitle());
        holder.tvAuthor.setText(series.getAuthor());

        int count = series.getBookCount();
        holder.tvCount.setText(count > 0 ? count + " томов" : "Серия");

        View.OnClickListener clickAction = v -> {
            if (listener != null) {
                listener.onSeriesClick(series);
            }
        };

        holder.itemView.setOnClickListener(clickAction);
        holder.btnOpen.setOnClickListener(clickAction);
    }

    @Override
    public int getItemCount() {
        return seriesList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAuthor, tvCount, btnOpen;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_series_title);
            tvAuthor = itemView.findViewById(R.id.tv_series_author);
            tvCount = itemView.findViewById(R.id.tv_series_count);
            btnOpen = itemView.findViewById(R.id.btn_open_series);
        }
    }
}
