package tw.fengqing.spring.springbucks.waiter.support;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.springframework.format.Formatter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Locale;

@Component
public class MoneyFormatter implements Formatter<Money> {
    /**
     * 處理 TWD 10.00 / 10.00 形式的字串
     * 校驗不太嚴密，僅作演示
     */
    @Override
    @NonNull
    public Money parse(@NonNull String text, @NonNull Locale locale) throws ParseException {
        if (NumberUtils.isParsable(text)) {
            return Money.of(CurrencyUnit.of("TWD"), NumberUtils.createBigDecimal(text));
        } else if (StringUtils.isNotEmpty(text)) {
            String[] split = StringUtils.split(text, " ");
            if (split != null && split.length == 2 && NumberUtils.isParsable(split[1])) {
                return Money.of(CurrencyUnit.of(split[0]),
                        NumberUtils.createBigDecimal(split[1]));
            } else {
                throw new ParseException(text, 0);
            }
        }
        throw new ParseException(text, 0);
    }

    @Override
    @NonNull
    public String print(@NonNull Money money, @NonNull Locale locale) {
        return money.getCurrencyUnit().getCode() + " " + money.getAmount();
    }
}
