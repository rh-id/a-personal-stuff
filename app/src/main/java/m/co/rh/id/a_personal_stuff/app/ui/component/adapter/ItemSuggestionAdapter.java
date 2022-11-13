package m.co.rh.id.a_personal_stuff.app.ui.component.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.function.Function;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.entity.Item;

public class ItemSuggestionAdapter extends ArrayAdapter<Item> {
    private Filter mFilter;
    private Function<String, Collection<Item>> mQuery;
    private int mResource;

    public ItemSuggestionAdapter(@NonNull Context context, int resource, Function<String, Collection<Item>> query) {
        super(context, resource);
        mResource = resource;
        mQuery = query;
        initFilter();
    }

    private void initFilter() {
        mFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                if (charSequence == null) return null;
                Collection<Item> resultList = mQuery.apply(charSequence.toString());
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
                    addAll((Collection<Item>) filterResults.values);
                }
            }
        };
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return mFilter;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(getContext()).inflate(mResource, parent, false);
        }

        Item item = getItem(position);

        if (item != null) {
            TextView barcode = v.findViewById(R.id.text_barcode);
            if (barcode != null) {
                barcode.setText(item.barcode);
            }
            TextView name = v.findViewById(R.id.text_name);
            if (name != null) {
                name.setText(item.name);
            }
        }

        return v;
    }
}
