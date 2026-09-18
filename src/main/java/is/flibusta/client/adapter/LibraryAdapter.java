package is.flibusta.client.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.R;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.ViewHolder> {
    private final Context context;
    private final List<Book> books;
    private final DatabaseHelper db;
    private Runnable onDataChanged;

    public interface OnLibraryItemClickListener {
        void onBookClick(Book book);
    }

    private OnLibraryItemClickListener clickListener;

    public LibraryAdapter(Context context, List<Book> books, Runnable onDataChanged) {
        this.context = context;
        this.books = books != null ? books : new ArrayList<>();
        this.db = new DatabaseHelper(context);
        this.onDataChanged = onDataChanged;
    }

    public void setClickListener(OnLibraryItemClickListener clickListener) {
        this.clickListener = clickListener;
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
        View view = LayoutInflater.from(context).inflate(R.layout.item_library_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Book book = books.get(position);

        holder.tvTitle.setText(book.getTitle());
        holder.tvAuthor.setText(book.getAuthor());
        holder.tvStatus.setText(book.getStatus());
        holder.tvDate.setText(book.getDateAdded());

        ImageLoader.loadCover(holder.ivCover, book.getCoverUrl());

        holder.itemView.setContentDescription(book.getTitle() + ", автор: " + book.getAuthor() + ", статус: " + book.getStatus());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onBookClick(book);
            }
        });

        // Status click -> Change status dialog
        holder.tvStatus.setContentDescription("Статус книги: " + book.getStatus() + ". Нажмите, чтобы изменить статус");
        holder.tvStatus.setOnClickListener(v -> {
            String[] statuses = new String[]{"Читаю", "Прочитано", "В планах"};
            new AlertDialog.Builder(context)
                    .setTitle("Статус книги")
                    .setItems(statuses, (dialog, which) -> {
                        String newStatus = statuses[which];
                        db.updateBookStatus(book.getId(), newStatus);
                        book.setStatus(newStatus);
                        notifyItemChanged(holder.getAdapterPosition());
                        Toast.makeText(context, "Статус изменен: " + newStatus, Toast.LENGTH_SHORT).show();
                        if (onDataChanged != null) onDataChanged.run();
                    })
                    .show();
        });

        // Delete button
        holder.btnDelete.setContentDescription("Удалить книгу из библиотеки: " + book.getTitle());
        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Удалить с полки?")
                    .setMessage("Удалить «" + book.getTitle() + "» из вашей библиотеки?")
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        int pos = holder.getAdapterPosition();
                        db.removeBook(book.getId());
                        books.remove(pos);
                        notifyItemRemoved(pos);
                        Toast.makeText(context, "Книга удалена из библиотеки", Toast.LENGTH_SHORT).show();
                        if (onDataChanged != null) onDataChanged.run();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        // Read button
        holder.btnRead.setContentDescription("Читать книгу: " + book.getTitle());
        holder.btnRead.setOnClickListener(v -> {
            if (book.getLocalPath() != null && !book.getLocalPath().isEmpty()) {
                BookDownloader.openBook(context, book);
            } else {
                Toast.makeText(context, "Книга еще не скачана на устройство. Скачиваем...", Toast.LENGTH_SHORT).show();
                BookDownloader.downloadBook(context, book, book.getFormat());
            }
        });
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvTitle, tvAuthor, tvStatus, tvDate;
        TextView btnDelete, btnRead;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_lib_cover);
            tvTitle = itemView.findViewById(R.id.tv_lib_title);
            tvAuthor = itemView.findViewById(R.id.tv_lib_author);
            tvStatus = itemView.findViewById(R.id.tv_lib_status);
            tvDate = itemView.findViewById(R.id.tv_lib_date);
            btnDelete = itemView.findViewById(R.id.btn_lib_delete);
            btnRead = itemView.findViewById(R.id.btn_lib_read);
        }
    }
}
