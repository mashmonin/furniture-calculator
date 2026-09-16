package com.example.furniturecalculator.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.GlazingOption;

public interface GlazingOptionRepository extends JpaRepository<GlazingOption, Long> {

    List<GlazingOption> findByLeafTypeId(Long leafTypeId);

    // Запрос расчёта стоимости и каталог ссылаются на glazing_type.id (глобальный, не владение) — по
    // аналогии с MirrorFinishOptionRepository.findByMirrorFinishTypeIdAndLeafTypeId (см. change
    // add-glazing-price-surcharge). Эта проверка подтверждает, что выбранный вид остекления допустим
    // именно для данного leaf_type.
    Optional<GlazingOption> findByGlazingTypeIdAndLeafTypeId(Long glazingTypeId, Long leafTypeId);
}
