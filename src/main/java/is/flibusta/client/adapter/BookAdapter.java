package is.flibusta.client.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.R;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;

import java.util.ArrayList;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.ViewHolder> {
    private final Context context;
    private final List<Book> books;
    private final DatabaseHelper db;

    public interface OnBookActionListener {
        void onDownload(Book book);
        void onAddToLibrary(Book book);
    }

    private OnBookActionListener listener;

    public BookAdapter(Context context, List<Book> books) {
        this.context = context;
        this.books = books != null ? books : new ArrayList<>();
        this.db = new DatabaseHelper(context);
    }

    public void setListener(OnBookActionListener listener) {
        this.listener = listener;
    }

    public void updateList(List<Book> newBooks) {
        this.books.clear();
        if (newBooks != null) {
            this.books.addAll(newBooks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Book book = books.get(position);

        holder.tvTitle.setText(book.getTitle());
        holder.tvAuthor.setText(book.getAuthor());
        holder.tvGenre.setText(book.getGenre());
        holder.tvSize.setText(book.getSize());

        boolean inLib = db.isBookInLibrary(book.getId());
        if (inLib) {
            holder.btnLibrary.setText(R.string.btn_in_library);
            holder.btnLibrary.setTextColor(context.getResources().getColor(R.color.accent_green));
        } else {
            holder.btnLibrary.setText(R.string.btn_to_library);
            holder.btnLibrary.setTextColor(context.getResources().getColor(R.color.text_secondary));
        }

        holder.btnLibrary.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddToLibrary(book);
            } else {
                db.addBook(book);
                holder.btnLibrary.setText(R.string.btn_in_library);
                holder.btnLibrary.setTextColor(context.getResources().getColor(R.color.accent_green));
                Toast.makeText(context, "Добавлено на полку: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownload(book);
            } else {
                BookDownloader.downloadBook(context, book, "fb2");
            }
        });
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAuthor, tvGenre, tvSize;
        TextView btnLibrary, btnDownload;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_book_title);
            tvAuthor = itemView.findViewById(R.id.tv_book_author);
            tvGenre = itemView.findViewById(R.id.tv_book_genre);
            tvSize = itemView.findViewById(R.id.tv_book_size);
            btnLibrary = itemView.findViewById(R.id.btn_add_library);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }
}
