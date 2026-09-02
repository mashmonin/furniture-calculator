package com.example.furniturecalculator.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.MirrorFinishOption;

public interface MirrorFinishOptionRepository extends JpaRepository<MirrorFinishOption, Long> {

    List<MirrorFinishOption> findByLeafTypeId(Long leafTypeId);

    // Запрос расчёта стоимости и каталог ссылаются на mirror_finish_type.id (глобальный, не владение) —
    // см. change add-mirror-finish-leaf-option, решение «Каталог и запрос ссылаются на mirror_finish_type.id».
    // Эта проверка подтверждает, что выбранное исполнение допустимо именно для данного leaf_type.
    Optional<MirrorFinishOption> findByMirrorFinishTypeIdAndLeafTypeId(Long mirrorFinishTypeId, Long leafTypeId);
}
