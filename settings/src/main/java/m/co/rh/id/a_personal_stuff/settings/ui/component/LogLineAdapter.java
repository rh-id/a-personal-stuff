package m.co.rh.id.a_personal_stuff.settings.ui.component;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.settings.R;

public class LogLineAdapter extends RecyclerView.Adapter<LogLineAdapter.LogLineViewHolder> {

    private final List<String> mLogLines;

    public LogLineAdapter() {
        mLogLines = new ArrayList<>();
    }

    @NonNull
    @Override
    public LogLineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_line, parent, false);
        return new LogLineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogLineViewHolder holder, int position) {
        holder.mTextView.setText(mLogLines.get(position));
    }

    @Override
    public int getItemCount() {
        return mLogLines.size();
    }

    public void setLogLines(List<String> logLines) {
        mLogLines.clear();
        mLogLines.addAll(logLines);
        notifyDataSetChanged();
    }

    public List<String> getLogLines() {
        return mLogLines;
    }

    static class LogLineViewHolder extends RecyclerView.ViewHolder {
        final TextView mTextView;

        public LogLineViewHolder(@NonNull View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.text_log_line);
        }
    }
}
