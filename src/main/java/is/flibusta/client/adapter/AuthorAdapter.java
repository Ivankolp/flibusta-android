package is.flibusta.client.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.R;
import is.flibusta.client.data.Author;

import java.util.ArrayList;
import java.util.List;

public class AuthorAdapter extends RecyclerView.Adapter<AuthorAdapter.AuthorViewHolder> {

    public interface OnAuthorClickListener {
        void onAuthorClick(Author author);
    }

    private final Context context;
    private List<Author> authors;
    private OnAuthorClickListener listener;

    public AuthorAdapter(Context context, List<Author> authors) {
        this.context = context;
        this.authors = authors != null ? authors : new ArrayList<>();
    }

    public void setListener(OnAuthorClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<Author> newAuthors) {
        this.authors = newAuthors != null ? newAuthors : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void appendList(List<Author> moreAuthors) {
        if (moreAuthors != null && !moreAuthors.isEmpty()) {
            int start = this.authors.size();
            this.authors.addAll(moreAuthors);
            notifyItemRangeInserted(start, moreAuthors.size());
        }
    }

    @NonNull
    @Override
    public AuthorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_author, parent, false);
        return new AuthorViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AuthorViewHolder holder, int position) {
        Author author = authors.get(position);
        holder.tvName.setText(author.getName());

        int count = author.getBookCount();
        String countStr;
        if (count > 0) {
            countStr = formatBookCount(count);
        } else {
            countStr = "Автор";
        }
        holder.tvCount.setText(countStr);

        String a11y = "Автор: " + author.getName() + ", " + countStr + ". Нажмите, чтобы открыть книги автора";
        holder.itemView.setContentDescription(a11y);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAuthorClick(author);
            }
        });
    }

    private String formatBookCount(int count) {
        int rem100 = count % 100;
        int rem10 = count % 10;
        if (rem100 >= 11 && rem100 <= 19) {
            return count + " книг";
        }
        if (rem10 == 1) {
            return count + " книга";
        }
        if (rem10 >= 2 && rem10 <= 4) {
            return count + " книги";
        }
        return count + " книг";
    }

    @Override
    public int getItemCount() {
        return authors.size();
    }

    static class AuthorViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvCount;
        View btnOpen;

        public AuthorViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_author_name);
            tvCount = itemView.findViewById(R.id.tv_author_count);
            btnOpen = itemView.findViewById(R.id.btn_open_author);
        }
    }
}
