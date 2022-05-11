package m.co.rh.id.a_personal_stuff.base.ui.component.adapter;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.function.Function;

public class SuggestionAdapter extends ArrayAdapter<String> {
    private Filter mFilter;
    private Function<String, Collection<String>> mQuery;

    public SuggestionAdapter(@NonNull Context context, Function<String, Collection<String>> query) {
        this(context, android.R.layout.select_dialog_item, query);
    }

    public SuggestionAdapter(@NonNull Context context, int resource, Function<String, Collection<String>> query) {
        super(context, resource);
        mQuery = query;
        initFilter();
    }

    private void initFilter() {
        mFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                if (charSequence == null) return null;
                Collection<String> resultList = mQuery.apply(charSequence.toString());
                FilterResults filterResults = new FilterResults();
                filterResults.values = resultList;
                filterResults.count = resultList.size();
                return filterResults;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                if (filterResults != null) {
                    clear();
                    addAll((Collection<String>) filterResults.values);
                }
            }
        };
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return mFilter;
    }
}
