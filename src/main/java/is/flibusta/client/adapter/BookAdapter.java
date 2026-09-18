package is.flibusta.client.adapter;

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

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.ViewHolder> {
    private final Context context;
    private final List<Book> books;
    private final DatabaseHelper db;

    public interface OnBookActionListener {
        void onBookClick(Book book);
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

    public void addBooks(List<Book> moreBooks) {
        if (moreBooks != null && !moreBooks.isEmpty()) {
            int startPos = this.books.size();
            this.books.addAll(moreBooks);
            notifyItemRangeInserted(startPos, moreBooks.size());
        }
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

        String desc = book.getDescription();
        if (desc != null && !desc.isEmpty()) {
            holder.tvDesc.setVisibility(View.VISIBLE);
            holder.tvDesc.setText(desc);
        } else {
            holder.tvDesc.setVisibility(View.GONE);
        }

        // Load dynamic cover image via ImageLoader
        ImageLoader.loadCover(holder.ivCover, book.getCoverUrl());

        boolean inLib = db.isBookInLibrary(book.getId());
        if (inLib) {
            holder.btnLibrary.setText("В библиотеке");
            holder.btnLibrary.setTextColor(context.getResources().getColor(R.color.accent_green));
            holder.btnLibrary.setContentDescription("Книга в библиотеке: " + book.getTitle());
        } else {
            holder.btnLibrary.setText("На полку");
            holder.btnLibrary.setTextColor(context.getResources().getColor(R.color.text_secondary));
            holder.btnLibrary.setContentDescription("Добавить на полку: " + book.getTitle());
        }

        holder.btnDownload.setText("FB2");
        holder.btnDownload.setContentDescription("Скачать в формате FB2: " + book.getTitle());

        // Card accessibility
        holder.itemView.setContentDescription(book.getTitle() + ", автор: " + book.getAuthor() + ", жанр: " + book.getGenre());

        // Clicking the card opens full book details!
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookClick(book);
            }
        });

        holder.btnLibrary.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddToLibrary(book);
            } else {
                db.addBook(book);
                holder.btnLibrary.setText("В библиотеке");
                holder.btnLibrary.setTextColor(context.getResources().getColor(R.color.accent_green));
                holder.btnLibrary.setContentDescription("Книга в библиотеке: " + book.getTitle());
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
        ImageView ivCover;
        TextView tvTitle, tvAuthor, tvDesc, tvGenre, tvSize;
        TextView btnLibrary, btnDownload;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_book_cover);
            tvTitle = itemView.findViewById(R.id.tv_book_title);
            tvAuthor = itemView.findViewById(R.id.tv_book_author);
            tvDesc = itemView.findViewById(R.id.tv_book_desc);
            tvGenre = itemView.findViewById(R.id.tv_book_genre);
            tvSize = itemView.findViewById(R.id.tv_book_size);
            btnLibrary = itemView.findViewById(R.id.btn_add_library);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }
}
