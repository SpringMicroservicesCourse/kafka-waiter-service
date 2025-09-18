package tw.fengqing.spring.springbucks.waiter.model;
    
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

/**
 * JPA 屬性轉換器
 * 用於在資料庫和 Java 物件之間轉換 Money 類型
 */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, Long> {
    /**
     * 將 Money 物件轉換為資料庫中的長整數值
     * 將金額轉換為最小貨幣單位（分）
     * 
     * @param attribute Money 物件
     * @return 轉換後的長整數值
     */
    @Override
    public Long convertToDatabaseColumn(Money attribute) {
        return attribute == null ? null : attribute.getAmountMinorLong();
    }

    /**
     * 將資料庫中的長整數值轉換為 Money 物件
     * 將最小貨幣單位（分）轉換為 Money 物件
     * 
     * @param dbData 資料庫中的長整數值
     * @return 轉換後的 Money 物件
     */
    @Override
    public Money convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : Money.ofMinor(CurrencyUnit.of("TWD"), dbData);
    }
} 