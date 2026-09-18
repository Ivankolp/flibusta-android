package is.flibusta.client.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.R;
import is.flibusta.client.data.GenreItem;

import java.util.ArrayList;
import java.util.List;

public class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.ViewHolder> {
    private final Context context;
    private final List<GenreItem> genres;

    public interface OnGenreClickListener {
        void onGenreClick(GenreItem genre);
    }

    private OnGenreClickListener listener;

    public GenreAdapter(Context context, List<GenreItem> genres) {
        this.context = context;
        this.genres = genres != null ? genres : new ArrayList<>();
    }

    public void setListener(OnGenreClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<GenreItem> newList) {
        this.genres.clear();
        if (newList != null) {
            this.genres.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_genre, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GenreItem item = genres.get(position);

        holder.tvTitle.setText(item.getTitle());

        String desc = item.getCountOrDesc();
        if (desc != null && !desc.isEmpty()) {
            holder.tvDesc.setVisibility(View.VISIBLE);
            holder.tvDesc.setText(desc);
            holder.itemView.setContentDescription(item.getTitle() + ", " + desc);
        } else {
            holder.tvDesc.setVisibility(View.GONE);
            holder.itemView.setContentDescription(item.getTitle());
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGenreClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return genres.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc, tvBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_genre_title);
            tvDesc = itemView.findViewById(R.id.tv_genre_desc);
            tvBadge = itemView.findViewById(R.id.tv_genre_badge);
        }
    }
}
