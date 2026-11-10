package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.os.ConfigurationCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.StatisticsCmd;
import m.co.rh.id.a_personal_stuff.app.ui.model.StatisticsData;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Statistics page: monthly spend (bar chart over the last 12 months),
 * most-consumed items (ranked by total used amount) and the inventory
 * summary (total value + expired count). Data comes from the blocking
 * {@link StatisticsCmd} and the page live-refreshes on item, usage and
 * purchase changes (same merge-and-debounce approach as DashboardCmd, so an
 * import's event burst recomputes only once).
 */
public class StatisticsPage extends StatefulView<Activity> implements RequireComponent<Provider> {

    private static final String TAG = StatisticsPage.class.getName();

    /**
     * Delays the refresh triggered by bursts of change events (e.g. an import
     * emits one event per item) so the statistics recompute only once. Same
     * constant as DashboardCmd's refresh debounce.
     */
    private static final long REFRESH_DEBOUNCE_MILLIS = 700;

    /**
     * Parses the service's 'yyyy-MM' month keys. Locale.US: the key is data
     * (matching the DAO's strftime output), not display text.
     */
    private static final String MONTH_KEY_FORMAT = "yyyy-MM";

    @NavInject
    private transient INavigator mNavigator;
    // NOT transient (matches every other page): the navigator's restore path
    // (initNavigatorState → injectStatefulView → navInjectStatefulViews)
    // never SETS a StatefulView-typed field, it only injects INTO an already
    // non-null instance — so the constructor-created AppBarSV must survive
    // route-stack serialization to be usable in createView after restore
    @NavInject
    private AppBarSV mAppBarSV;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient StatisticsCmd mStatisticsCmd;
    private transient ILogger mLogger;

    private transient BarChart mBarChart;
    private transient View mContainerChartSection;
    private transient View mContainerMostConsumedSection;
    private transient ViewGroup mContainerMostConsumed;
    private transient View mContainerContent;
    private transient View mTextEmpty;
    private transient TextView mTextInventoryValue;
    private transient TextView mTextExpiredCount;
    private transient Locale mLocale;
    private transient NumberFormat mMoneyFormat;
    private transient SimpleDateFormat mMonthLabelFormat;
    private transient SimpleDateFormat mMonthKeyFormat;
    private transient int mBarColor;
    private transient int mTextColor;

    public StatisticsPage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mStatisticsCmd = mSvProvider.get(StatisticsCmd.class);
        mLogger = mSvProvider.get(ILogger.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_statistics, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_statistics));
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        mTextEmpty = rootLayout.findViewById(R.id.text_statistics_empty);
        mContainerContent = rootLayout.findViewById(R.id.container_statistics_content);
        mBarChart = rootLayout.findViewById(R.id.chart_statistics);
        mContainerChartSection = rootLayout.findViewById(R.id.container_statistics_chart_section);
        mContainerMostConsumedSection = rootLayout.findViewById(R.id.container_statistics_most_consumed_section);
        mContainerMostConsumed = rootLayout.findViewById(R.id.container_statistics_most_consumed);
        mTextInventoryValue = rootLayout.findViewById(R.id.text_statistics_inventory_value);
        mTextExpiredCount = rootLayout.findViewById(R.id.text_statistics_expired_count);
        mLocale = ConfigurationCompat.getLocales(activity.getResources().getConfiguration()).get(0);
        mMoneyFormat = NumberFormat.getNumberInstance(mLocale);
        mMoneyFormat.setMaximumFractionDigits(2);
        mMonthLabelFormat = new SimpleDateFormat("MMM yy", mLocale);
        mMonthKeyFormat = new SimpleDateFormat(MONTH_KEY_FORMAT, Locale.US);
        // plain color resources with a values-night override follow the dark
        // theme automatically; the bar itself follows the app theme's primary
        // (base R: android.nonTransitiveRClass=true, so library resources are
        // NOT in the app module's R class)
        mTextColor = ContextCompat.getColor(activity,
                m.co.rh.id.a_personal_stuff.base.R.color.daynight_gray_700_white);
        mBarColor = resolveThemeColor(activity, androidx.appcompat.R.attr.colorPrimary);
        setupChart();
        mRxDisposer.add("createView_onStatisticsChange",
                refreshTrigger()
                        .debounce(REFRESH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(ignored -> refreshStatistics(),
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
        refreshStatistics();
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mRxDisposer = null;
        mStatisticsCmd = null;
        mLogger = null;
        mBarChart = null;
        mContainerChartSection = null;
        mContainerMostConsumedSection = null;
        mContainerMostConsumed = null;
        mContainerContent = null;
        mTextEmpty = null;
        mTextInventoryValue = null;
        mTextExpiredCount = null;
        mLocale = null;
        mMoneyFormat = null;
        mMonthLabelFormat = null;
        mMonthKeyFormat = null;
    }

    /**
     * Merges the item / usage / purchase any-change flows: all three feed the
     * aggregates (items move inventory value, expiry count and names; usage
     * moves the ranking; purchases move the monthly spend).
     */
    private Flowable<Object> refreshTrigger() {
        ItemChangeNotifier itemChangeNotifier = mSvProvider.get(ItemChangeNotifier.class);
        ItemUsageChangeNotifier itemUsageChangeNotifier = mSvProvider.get(ItemUsageChangeNotifier.class);
        ItemPurchaseChangeNotifier itemPurchaseChangeNotifier = mSvProvider.get(ItemPurchaseChangeNotifier.class);
        return Flowable.merge(
                itemChangeNotifier.getAnyItemChangeFlow().map(itemState -> (Object) itemState),
                itemUsageChangeNotifier.getAnyItemUsageChangeFlow().map(itemId -> (Object) itemId),
                itemPurchaseChangeNotifier.getAnyItemPurchaseChangeFlow().map(itemId -> (Object) itemId));
    }

    /**
     * Keyed RxDisposer subscription: a re-trigger replaces the previous
     * pending computation instead of stacking duplicates. The command owns
     * the background thread (shared weight-5 pool); results render on the
     * main thread.
     */
    private void refreshStatistics() {
        mRxDisposer.add("statistics_refresh",
                mStatisticsCmd.execute()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::renderStatistics,
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
    }

    /**
     * Static chart chrome that never changes between renders: no description
     * or legend (sections are labeled in the page), all gestures off (the
     * page scrolls, the chart is display-only), money formatting on the
     * left axis (same display formatting the dashboard's inventory value
     * uses) and no right axis.
     */
    private void setupChart() {
        mBarChart.getDescription().setEnabled(false);
        mBarChart.getLegend().setEnabled(false);
        mBarChart.setTouchEnabled(false);
        mBarChart.setDrawGridBackground(false);
        XAxis xAxis = mBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(mTextColor);
        YAxis leftAxis = mBarChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setTextColor(mTextColor);
        leftAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return mMoneyFormat.format(value);
            }
        });
        mBarChart.getAxisRight().setEnabled(false);
    }

    private void renderStatistics(StatisticsData statisticsData) {
        // the subscription itself is disposed via mSvProvider.dispose(), but
        // a result already queued on the main thread can still arrive after
        // dispose() completes — the guard skips rendering into dead views
        if (mContainerContent == null) {
            return;
        }
        if (statisticsData.isEmpty()) {
            mTextEmpty.setVisibility(View.VISIBLE);
            mContainerContent.setVisibility(View.GONE);
            return;
        }
        mTextEmpty.setVisibility(View.GONE);
        mContainerContent.setVisibility(View.VISIBLE);
        renderChart(statisticsData);
        renderMostConsumed(statisticsData);
        if (statisticsData.inventoryValue != null) {
            mTextInventoryValue.setText(mMoneyFormat.format(statisticsData.inventoryValue));
        } else {
            mTextInventoryValue.setText("-");
        }
        mTextExpiredCount.setText(String.valueOf(statisticsData.expiredCount));
    }

    private void renderChart(StatisticsData statisticsData) {
        // the subscription itself is disposed via mSvProvider.dispose(), but
        // a result already queued on the main thread can still arrive after
        // dispose() completes — the guard skips rendering into dead views
        if (mContainerChartSection == null) {
            return;
        }
        List<StatisticsData.MonthlySpend> monthlySpendList = statisticsData.monthlySpend;
        List<BarEntry> barEntries = new ArrayList<>(monthlySpendList.size());
        List<String> monthLabels = new ArrayList<>(monthlySpendList.size());
        boolean hasSpend = false;
        for (int i = 0; i < monthlySpendList.size(); i++) {
            StatisticsData.MonthlySpend monthlySpend = monthlySpendList.get(i);
            barEntries.add(new BarEntry(i, (float) monthlySpend.total));
            monthLabels.add(formatMonthKey(monthlySpend.month));
            if (monthlySpend.total != 0) {
                hasSpend = true;
            }
        }
        if (!hasSpend) {
            // all-zero spend (e.g. expired-only data): MPAndroidChart auto-expands
            // the equal min/max axis to [-1, 1] and renders a degenerate box of
            // zero-height bars over a meaningless ±1 scale, so hide the whole
            // section like renderMostConsumed does for an empty ranking
            mContainerChartSection.setVisibility(View.GONE);
            return;
        }
        mContainerChartSection.setVisibility(View.VISIBLE);
        BarDataSet barDataSet = new BarDataSet(barEntries, "monthly spend");
        barDataSet.setColor(mBarColor);
        // per-bar value labels overlap at 12 bars (a formatted value needs
        // ~45-55dp, a bar slot is ~24dp) — the y-axis money scale carries
        // the numbers instead
        barDataSet.setDrawValues(false);
        BarData barData = new BarData(barDataSet);
        barData.setBarWidth(0.7f);
        mBarChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(monthLabels));
        mBarChart.setData(barData);
        mBarChart.invalidate();
    }

    private void renderMostConsumed(StatisticsData statisticsData) {
        // the subscription itself is disposed via mSvProvider.dispose(), but
        // a result already queued on the main thread can still arrive after
        // dispose() completes — the guard skips rendering into dead views
        if (mContainerMostConsumed == null) {
            return;
        }
        mContainerMostConsumed.removeAllViews();
        List<StatisticsData.MostConsumedItem> mostConsumedItems = statisticsData.mostConsumedItems;
        if (mostConsumedItems.isEmpty()) {
            mContainerMostConsumedSection.setVisibility(View.GONE);
            return;
        }
        mContainerMostConsumedSection.setVisibility(View.VISIBLE);
        Context context = mContainerMostConsumed.getContext();
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        int rank = 1;
        for (StatisticsData.MostConsumedItem mostConsumedItem : mostConsumedItems) {
            View rowView = layoutInflater.inflate(
                    R.layout.view_statistics_most_consumed_row,
                    mContainerMostConsumed, false);
            TextView textRank = rowView.findViewById(R.id.text_statistics_rank);
            textRank.setText(String.valueOf(rank));
            TextView textItemName = rowView.findViewById(R.id.text_statistics_item_name);
            textItemName.setText(mostConsumedItem.itemName);
            TextView textTotalAmount = rowView.findViewById(R.id.text_statistics_total_amount);
            textTotalAmount.setText(context.getString(R.string.amount_,
                    mostConsumedItem.totalAmount));
            mContainerMostConsumed.addView(rowView);
            rank++;
        }
    }

    /**
     * Formats a service month key ('yyyy-MM') for the chart axis, using the
     * same display formatting approach as the app's other date outputs
     * (SimpleDateFormat over the page locale).
     */
    private String formatMonthKey(String monthKey) {
        try {
            return mMonthLabelFormat.format(mMonthKeyFormat.parse(monthKey));
        } catch (ParseException e) {
            // unparseable key: show the raw key rather than losing the bar
            return monthKey;
        }
    }

    private int resolveThemeColor(Activity activity, int attr) {
        TypedValue typedValue = new TypedValue();
        activity.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}
