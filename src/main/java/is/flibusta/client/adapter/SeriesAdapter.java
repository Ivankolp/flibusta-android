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

    public void addSeries(List<Series> moreSeries) {
        if (moreSeries != null && !moreSeries.isEmpty()) {
            int startPos = this.seriesList.size();
            this.seriesList.addAll(moreSeries);
            notifyItemRangeInserted(startPos, moreSeries.size());
        }
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

        String author = series.getAuthor();
        boolean hasValidAuthor = author != null && !author.trim().isEmpty() && !author.equalsIgnoreCase("Цикл на Флибусте");
        if (hasValidAuthor) {
            holder.tvAuthor.setVisibility(View.VISIBLE);
            holder.tvAuthor.setText(author);
        } else {
            holder.tvAuthor.setVisibility(View.GONE);
        }

        int count = series.getBookCount();
        if (count > 1) {
            holder.tvCount.setText(count + " томов");
        } else {
            holder.tvCount.setText("Серия");
        }

        StringBuilder desc = new StringBuilder();
        desc.append("Цикл: ").append(series.getTitle());
        if (hasValidAuthor) {
            desc.append(", автор: ").append(author);
        }
        if (count > 1) {
            desc.append(", томов: ").append(count);
        }
        desc.append(". Нажмите дважды, чтобы открыть все книги цикла.");
        holder.itemView.setContentDescription(desc.toString());

        holder.btnOpen.setClickable(false);
        holder.btnOpen.setFocusable(false);
        holder.btnOpen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSeriesClick(series);
            }
        });
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
