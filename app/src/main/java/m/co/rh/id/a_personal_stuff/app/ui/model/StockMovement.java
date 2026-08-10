package m.co.rh.id.a_personal_stuff.app.ui.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;

public class StockMovement implements Serializable {
    public enum Type { USAGE, PURCHASE }

    public Type type;
    public Date date;
    public int signedAmount;
    public BigDecimal cost;
    public String description;
    public Long sourceId;
    public Object sourceState;

    public static Comparator<StockMovement> byDateDesc() {
        return (m1, m2) -> m2.date.compareTo(m1.date);
    }
}
