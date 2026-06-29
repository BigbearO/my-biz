package com.bigbear.ai.ai3d.domain.entity;

import com.bigbear.common.core.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("pre_consult_summary")
public class PreConsultSummaryEntity extends BaseEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    private Long id;

    private String chatId;
    private String chiefComplaint;
    private String presentIllness;
    private String pastHistory;
    private String medication;
    private String allergy;
    private String visitGoal;
    private String rawSummary;
}
