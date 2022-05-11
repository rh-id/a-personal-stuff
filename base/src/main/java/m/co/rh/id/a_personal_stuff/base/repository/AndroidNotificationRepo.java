package m.co.rh.id.a_personal_stuff.base.repository;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicInteger;

import m.co.rh.id.a_personal_stuff.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_personal_stuff.base.entity.AndroidNotification;
import m.co.rh.id.aprovider.Provider;

public class AndroidNotificationRepo {
    private static final String SHARED_PREFERENCES_NAME = "AndroidNotificationRepo";

    private SharedPreferences mSharedPreferences;
    private AndroidNotificationDao mAndroidNotificationDao;

    private AtomicInteger mRequestId;
    private String mRequestIdKey;

    public AndroidNotificationRepo(Provider provider) {
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        mAndroidNotificationDao = provider.get(AndroidNotificationDao.class);

        mRequestIdKey = SHARED_PREFERENCES_NAME
                + ".requestId";
        mRequestId = new AtomicInteger(mSharedPreferences.getInt(mRequestIdKey, 0));
    }

    public synchronized AndroidNotification findByRequestId(int requestId) {
        return mAndroidNotificationDao.findByRequestId(requestId);
    }

    public synchronized AndroidNotification findByGroupTagAndRefId(String groupKey, Long refId) {
        return mAndroidNotificationDao.findByGroupTagAndRefId(groupKey, refId);
    }

    public synchronized void insertNotification(AndroidNotification androidNotification) {
        androidNotification.requestId = mRequestId.getAndIncrement();
        androidNotification.id = mAndroidNotificationDao.insert(androidNotification);
        mSharedPreferences.edit().putInt(mRequestIdKey, mRequestId.get())
                .commit();
    }

    public synchronized void deleteNotificationByRequestId(int requestId) {
        mAndroidNotificationDao.deleteByRequestId(requestId);
    }

    public synchronized void deleteNotification(AndroidNotification androidNotification) {
        mAndroidNotificationDao.delete(androidNotification);
    }
}
