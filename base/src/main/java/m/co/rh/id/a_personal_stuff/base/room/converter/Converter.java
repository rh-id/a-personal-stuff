package m.co.rh.id.a_personal_stuff.base.room.converter;

import androidx.room.TypeConverter;

import java.math.BigDecimal;
import java.util.Date;

public class Converter {
    @TypeConverter
    public static Date dateFromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static BigDecimal stringToBigDecimal(String value) {
        BigDecimal result;
        if (value == null || value.isEmpty()) {
            result = null;
        } else {
            try {
                result = new BigDecimal(value);
            } catch (NumberFormatException e) {
                result = null;
            }
        }
        return result;
    }

    @TypeConverter
    public static String bigDecimalToString(BigDecimal bigDecimal) {
        String result;
        if (bigDecimal == null) {
            result = null;
        } else {
            result = bigDecimal.toString();
        }
        return result;
    }
}
