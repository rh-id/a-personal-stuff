package m.co.rh.id.a_personal_stuff.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import m.co.rh.id.a_personal_stuff.base.entity.AndroidNotification;

@Dao
public abstract class AndroidNotificationDao {

    @Query("SELECT * FROM android_notification WHERE request_id = :requestId")
    public abstract AndroidNotification findByRequestId(int requestId);

    @Query("SELECT * FROM android_notification WHERE group_key=:groupKey AND ref_id=:refId")
    public abstract AndroidNotification findByGroupTagAndRefId(String groupKey, Long refId);

    @Query("SELECT COUNT(id) FROM android_notification")
    public abstract long count();

    @Query("DELETE FROM android_notification WHERE request_id = :requestId")
    public abstract void deleteByRequestId(int requestId);

    @Insert
    public abstract long insert(AndroidNotification androidNotification);

    @Delete
    public abstract void delete(AndroidNotification androidNotification);
}
