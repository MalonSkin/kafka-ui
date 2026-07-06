import React from 'react';
import { MessageFilters } from 'components/Topics/Topic/Messages/Filters/Filters';
import { FilterEdit } from 'components/Topics/Topic/Messages/Filters/FilterModal';

import AddEditFilterContainer from './AddEditFilterContainer';
import * as S from './Filters.styled';
import { useTranslation } from 'react-i18next';

export interface EditFilterProps {
  editFilter: FilterEdit;
  toggleEditModal(): void;
  editSavedFilter(filter: FilterEdit): void;
}

const EditFilter: React.FC<EditFilterProps> = ({
  editFilter,
  toggleEditModal,
  editSavedFilter,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const onSubmit = (values: MessageFilters) => {
    editSavedFilter({ index: editFilter.index, filter: values });
    toggleEditModal();
  };
  return (
    <>
      <S.FilterTitle>{t('topic.editFilter')}</S.FilterTitle>
      <AddEditFilterContainer
        cancelBtnHandler={() => toggleEditModal()}
        submitBtnText={t('common.save')}
        inputDisplayNameDefaultValue={editFilter.filter.name}
        inputCodeDefaultValue={editFilter.filter.code}
        submitCallback={onSubmit}
      />
    </>
  );
};

export default EditFilter;