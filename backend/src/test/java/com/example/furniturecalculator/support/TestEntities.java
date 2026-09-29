package com.example.furniturecalculator.support;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;

import org.springframework.test.util.ReflectionTestUtils;

import com.example.furniturecalculator.domain.CatalogType;
import com.example.furniturecalculator.domain.ColourOption;
import com.example.furniturecalculator.domain.ColourType;
import com.example.furniturecalculator.domain.ConfigurationPrice;
import com.example.furniturecalculator.domain.DecorativeElementCategory;
import com.example.furniturecalculator.domain.DecorativeElementOption;
import com.example.furniturecalculator.domain.DecorativeElementType;
import com.example.furniturecalculator.domain.DecorativeElementWidthOption;
import com.example.furniturecalculator.domain.DimensionSurchargeRule;
import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.GlazingOption;
import com.example.furniturecalculator.domain.GlazingType;
import com.example.furniturecalculator.domain.HardwareCategory;
import com.example.furniturecalculator.domain.HardwareOption;
import com.example.furniturecalculator.domain.HardwareType;
import com.example.furniturecalculator.domain.LeafCollection;
import com.example.furniturecalculator.domain.LeafPanelType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.domain.MirrorFinishOption;
import com.example.furniturecalculator.domain.MirrorFinishType;
import com.example.furniturecalculator.domain.PogonazhSurchargeRule;
import com.example.furniturecalculator.domain.PostType;

// Сущности домена не имеют публичных конструкторов/сеттеров (только Hibernate field-access),
// поэтому тестовые фикстуры собираются через ReflectionTestUtils.
public final class TestEntities {

    private TestEntities() {
    }

    public static LeafType leafType(long id) {
        LeafType leafType = referenceType(LeafType.class, id);
        ReflectionTestUtils.setField(leafType, "collection", leafCollection(id));
        ReflectionTestUtils.setField(leafType, "panelType", LeafPanelType.BLIND);
        return leafType;
    }

    public static LeafCollection leafCollection(long id) {
        return referenceType(LeafCollection.class, id);
    }

    public static FrameType frameType(long id) {
        return referenceType(FrameType.class, id);
    }

    public static FrameType frameType(long id, String code) {
        FrameType type = frameType(id);
        ReflectionTestUtils.setField(type, "code", code);
        return type;
    }

    public static EdgeType edgeType(long id) {
        return referenceType(EdgeType.class, id);
    }

    public static EdgeType edgeType(long id, String code) {
        EdgeType type = edgeType(id);
        ReflectionTestUtils.setField(type, "code", code);
        return type;
    }

    public static DoorCasingType doorCasingType(long id) {
        return referenceType(DoorCasingType.class, id);
    }

    public static DoorCasingType doorCasingType(long id, String code) {
        DoorCasingType type = doorCasingType(id);
        ReflectionTestUtils.setField(type, "code", code);
        return type;
    }

    public static FrameExtensionsType frameExtensionsType(long id) {
        return referenceType(FrameExtensionsType.class, id);
    }

    public static FrameExtensionsType frameExtensionsType(long id, String code) {
        FrameExtensionsType type = frameExtensionsType(id);
        ReflectionTestUtils.setField(type, "code", code);
        return type;
    }

    public static LinerDimensionType linerDimensionType(long id) {
        return referenceType(LinerDimensionType.class, id);
    }

    public static LinerDimensionType linerDimensionType(long id, String code) {
        LinerDimensionType type = linerDimensionType(id);
        ReflectionTestUtils.setField(type, "code", code);
        return type;
    }

    public static ColourType colourType(long id) {
        return colourType(id, BigDecimal.ZERO);
    }

    public static ColourType colourType(long id, BigDecimal surchargePercent) {
        ColourType type = referenceType(ColourType.class, id);
        ReflectionTestUtils.setField(type, "surchargePercent", surchargePercent);
        return type;
    }

    public static PostType postType(long id) {
        return referenceType(PostType.class, id);
    }

    public static FramePost framePost(long id, BigDecimal retailPrice, BigDecimal dealerPrice, FrameType frameType) {
        FramePost post = instantiate(FramePost.class);
        ReflectionTestUtils.setField(post, "id", id);
        ReflectionTestUtils.setField(post, "frameType", frameType);
        ReflectionTestUtils.setField(post, "postType", postType(id));
        ReflectionTestUtils.setField(post, "quantity", 1);
        ReflectionTestUtils.setField(post, "retailPrice", retailPrice);
        ReflectionTestUtils.setField(post, "dealerPrice", dealerPrice);
        return post;
    }

    public static DoorConfiguration doorConfiguration(
            long id, LeafType leafType, FrameType frameType, EdgeType edgeType,
            DoorCasingType doorCasingType, FrameExtensionsType frameExtensionsType) {
        return doorConfiguration(id, leafType, frameType, edgeType, doorCasingType, frameExtensionsType, false);
    }

    public static DoorConfiguration doorConfigurationReverse(
            long id, LeafType leafType, FrameType frameType, EdgeType edgeType,
            DoorCasingType doorCasingType, FrameExtensionsType frameExtensionsType) {
        return doorConfiguration(id, leafType, frameType, edgeType, doorCasingType, frameExtensionsType, true);
    }

    // «Четверть» независим от is_reverse (см. change add-leaf-quarter-attribute) — перегрузка для тестов,
    // которым явно нужно задать оба значения независимо друг от друга. Существующие вызовы без hasQuarter
    // (в т.ч. doorConfiguration/doorConfigurationReverse выше) продолжают получать hasQuarter = false.
    public static DoorConfiguration doorConfiguration(
            long id, LeafType leafType, FrameType frameType, EdgeType edgeType,
            DoorCasingType doorCasingType, FrameExtensionsType frameExtensionsType, boolean isReverse, boolean hasQuarter) {
        DoorConfiguration configuration = doorConfiguration(id, leafType, frameType, edgeType, doorCasingType, frameExtensionsType, isReverse);
        ReflectionTestUtils.setField(configuration, "hasQuarter", hasQuarter);
        return configuration;
    }

    private static DoorConfiguration doorConfiguration(
            long id, LeafType leafType, FrameType frameType, EdgeType edgeType,
            DoorCasingType doorCasingType, FrameExtensionsType frameExtensionsType, boolean isReverse) {
        DoorConfiguration configuration = instantiate(DoorConfiguration.class);
        ReflectionTestUtils.setField(configuration, "id", id);
        ReflectionTestUtils.setField(configuration, "leafType", leafType);
        ReflectionTestUtils.setField(configuration, "frameType", frameType);
        ReflectionTestUtils.setField(configuration, "edgeType", edgeType);
        ReflectionTestUtils.setField(configuration, "doorCasingType", doorCasingType);
        ReflectionTestUtils.setField(configuration, "frameExtensionsType", frameExtensionsType);
        ReflectionTestUtils.setField(configuration, "reverse", isReverse);
        return configuration;
    }

    public static LinerDimensionOption linerDimensionOption(
            long id, LinerDimensionType dimensionType, BigDecimal value, boolean standard, CatalogType owner) {
        LinerDimensionOption option = instantiate(LinerDimensionOption.class);
        ReflectionTestUtils.setField(option, "id", id);
        ReflectionTestUtils.setField(option, "linerDimensionType", dimensionType);
        ReflectionTestUtils.setField(option, "value", value);
        ReflectionTestUtils.setField(option, "standard", standard);
        setOwner(option, owner);
        return option;
    }

    public static LinerDimensionOption linerDimensionOptionRange(
            long id, LinerDimensionType dimensionType, BigDecimal minValue, BigDecimal maxValue, boolean standard,
            CatalogType owner) {
        LinerDimensionOption option = linerDimensionOption(id, dimensionType, maxValue, standard, owner);
        ReflectionTestUtils.setField(option, "minValue", minValue);
        return option;
    }

    // Для владельцев, у которых физическое value отличается от границ диапазона родительского размера
    // (например, короб «НЕО» — см. change link-frame-neo-height-to-leaf-height), в отличие от кромки,
    // где value исторически совпадает с верхней границей диапазона (см. перегрузку выше).
    public static LinerDimensionOption linerDimensionOptionRange(
            long id, LinerDimensionType dimensionType, BigDecimal value, BigDecimal minValue, BigDecimal maxValue,
            boolean standard, CatalogType owner) {
        LinerDimensionOption option = linerDimensionOption(id, dimensionType, value, standard, owner);
        ReflectionTestUtils.setField(option, "minValue", minValue);
        ReflectionTestUtils.setField(option, "maxValue", maxValue);
        return option;
    }

    public static DimensionSurchargeRule dimensionSurchargeRule(
            long id, LinerDimensionType dimensionType, BigDecimal value, BigDecimal surchargePercent) {
        DimensionSurchargeRule rule = instantiate(DimensionSurchargeRule.class);
        ReflectionTestUtils.setField(rule, "id", id);
        ReflectionTestUtils.setField(rule, "linerDimensionType", dimensionType);
        ReflectionTestUtils.setField(rule, "value", value);
        ReflectionTestUtils.setField(rule, "surchargePercent", surchargePercent);
        return rule;
    }

    // Ровно один из frameType/doorCasingType/frameExtensionsType должен быть не-null — как и в самой
    // сущности (см. миграцию 0094-pogonazh-surcharge-rule.yaml, CHECK-constraint single-owner).
    public static PogonazhSurchargeRule pogonazhSurchargeRule(
            long id, FrameType frameType, DoorCasingType doorCasingType, FrameExtensionsType frameExtensionsType,
            BigDecimal value, BigDecimal surchargePercent) {
        PogonazhSurchargeRule rule = instantiate(PogonazhSurchargeRule.class);
        ReflectionTestUtils.setField(rule, "id", id);
        ReflectionTestUtils.setField(rule, "frameType", frameType);
        ReflectionTestUtils.setField(rule, "doorCasingType", doorCasingType);
        ReflectionTestUtils.setField(rule, "frameExtensionsType", frameExtensionsType);
        ReflectionTestUtils.setField(rule, "value", value);
        ReflectionTestUtils.setField(rule, "surchargePercent", surchargePercent);
        return rule;
    }

    public static MirrorFinishType mirrorFinishType(long id, BigDecimal surchargePercent) {
        MirrorFinishType type = instantiate(MirrorFinishType.class);
        ReflectionTestUtils.setField(type, "id", id);
        ReflectionTestUtils.setField(type, "name", "Исполнение зеркала " + id);
        ReflectionTestUtils.setField(type, "surchargePercent", surchargePercent);
        return type;
    }

    public static MirrorFinishOption mirrorFinishOption(long id, MirrorFinishType mirrorFinishType, LeafType leafType) {
        MirrorFinishOption option = instantiate(MirrorFinishOption.class);
        ReflectionTestUtils.setField(option, "id", id);
        ReflectionTestUtils.setField(option, "mirrorFinishType", mirrorFinishType);
        ReflectionTestUtils.setField(option, "leafType", leafType);
        return option;
    }

    public static GlazingType glazingType(long id, BigDecimal surchargePercent) {
        GlazingType type = instantiate(GlazingType.class);
        ReflectionTestUtils.setField(type, "id", id);
        ReflectionTestUtils.setField(type, "name", "Вид остекления " + id);
        ReflectionTestUtils.setField(type, "surchargePercent", surchargePercent);
        return type;
    }

    public static GlazingOption glazingOption(long id, GlazingType glazingType, LeafType leafType) {
        GlazingOption option = instantiate(GlazingOption.class);
        ReflectionTestUtils.setField(option, "id", id);
        ReflectionTestUtils.setField(option, "glazingType", glazingType);
        ReflectionTestUtils.setField(option, "leafType", leafType);
        return option;
    }

    public static ColourOption colourOption(long id, ColourType colourType, CatalogType owner) {
        ColourOption option = instantiate(ColourOption.class);
        ReflectionTestUtils.setField(option, "id", id);
        ReflectionTestUtils.setField(option, "colourType", colourType);
        setOwner(option, owner);
        return option;
    }

    public static ConfigurationPrice configurationPrice(
            long id, BigDecimal retailPrice, BigDecimal dealerPrice, CatalogType owner,
            LinerDimensionOption lengthOption, LinerDimensionOption heightOption,
            LinerDimensionOption thicknessOption, ColourOption colourOption) {
        ConfigurationPrice price = instantiate(ConfigurationPrice.class);
        ReflectionTestUtils.setField(price, "id", id);
        ReflectionTestUtils.setField(price, "retailPrice", retailPrice);
        ReflectionTestUtils.setField(price, "dealerPrice", dealerPrice);
        ReflectionTestUtils.setField(price, "lengthOption", lengthOption);
        ReflectionTestUtils.setField(price, "heightOption", heightOption);
        ReflectionTestUtils.setField(price, "thicknessOption", thicknessOption);
        ReflectionTestUtils.setField(price, "colourOption", colourOption);
        setOwner(price, owner);
        return price;
    }

    public static HardwareCategory hardwareCategory(long id) {
        return referenceType(HardwareCategory.class, id);
    }

    public static HardwareType hardwareType(long id, HardwareCategory category) {
        HardwareType type = referenceType(HardwareType.class, id);
        ReflectionTestUtils.setField(type, "unit", "шт");
        ReflectionTestUtils.setField(type, "hardwareCategory", category);
        return type;
    }

    public static HardwareOption hardwareOption(
            long id, String colourName, BigDecimal retailPrice, BigDecimal dealerPrice, HardwareType type) {
        HardwareOption option = instantiate(HardwareOption.class);
        ReflectionTestUtils.setField(option, "id", id);
        ReflectionTestUtils.setField(option, "colourName", colourName);
        ReflectionTestUtils.setField(option, "retailPrice", retailPrice);
        ReflectionTestUtils.setField(option, "dealerPrice", dealerPrice);
        ReflectionTestUtils.setField(option, "hardwareType", type);
        return option;
    }

    public static DecorativeElementCategory decorativeElementCategory(long id) {
        return referenceType(DecorativeElementCategory.class, id);
    }

    public static DecorativeElementType decorativeElementType(
            long id, BigDecimal lengthMm, BigDecimal retailPrice, BigDecimal dealerPrice, DecorativeElementCategory category) {
        DecorativeElementType type = referenceType(DecorativeElementType.class, id);
        ReflectionTestUtils.setField(type, "lengthMm", lengthMm);
        ReflectionTestUtils.setField(type, "retailPrice", retailPrice);
        ReflectionTestUtils.setField(type, "dealerPrice", dealerPrice);
        ReflectionTestUtils.setField(type, "decorativeElementCategory", category);
        return type;
    }

    public static DecorativeElementWidthOption decorativeElementWidthOption(
            long id, DecorativeElementType type, BigDecimal minValue, BigDecimal maxValue, BigDecimal widthMm) {
        DecorativeElementWidthOption option = instantiate(DecorativeElementWidthOption.class);
        ReflectionTestUtils.setField(option, "id", id);
        ReflectionTestUtils.setField(option, "decorativeElementType", type);
        ReflectionTestUtils.setField(option, "minValue", minValue);
        ReflectionTestUtils.setField(option, "maxValue", maxValue);
        ReflectionTestUtils.setField(option, "widthMm", widthMm);
        return option;
    }

    public static DecorativeElementOption decorativeElementOption(long id, DecorativeElementType type, LeafType leafType) {
        DecorativeElementOption option = instantiate(DecorativeElementOption.class);
        ReflectionTestUtils.setField(option, "id", id);
        ReflectionTestUtils.setField(option, "decorativeElementType", type);
        ReflectionTestUtils.setField(option, "leafType", leafType);
        return option;
    }

    private static void setOwner(Object target, CatalogType owner) {
        String field = switch (owner) {
            case LeafType t -> "leafType";
            case FrameType t -> "frameType";
            case EdgeType t -> "edgeType";
            case DoorCasingType t -> "doorCasingType";
            case FrameExtensionsType t -> "frameExtensionsType";
            default -> throw new IllegalArgumentException("Неизвестный тип владельца: " + owner.getClass());
        };
        ReflectionTestUtils.setField(target, field, owner);
    }

    private static <T> T referenceType(Class<T> type, long id) {
        T instance = instantiate(type);
        ReflectionTestUtils.setField(instance, "id", id);
        ReflectionTestUtils.setField(instance, "code", type.getSimpleName() + "-" + id);
        ReflectionTestUtils.setField(instance, "name", type.getSimpleName() + " " + id);
        return instance;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось создать тестовый экземпляр " + type, e);
        }
    }
}
