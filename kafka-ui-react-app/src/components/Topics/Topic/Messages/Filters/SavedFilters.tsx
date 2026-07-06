import React, { FC } from 'react';
import { Button } from 'components/common/Button/Button';
import DeleteIcon from 'components/common/Icons/DeleteIcon';
import { useConfirm } from 'lib/hooks/useConfirm';

import * as S from './Filters.styled';
import { useTranslation } from 'react-i18next';
import { MessageFilters } from './Filters';

export interface Props {
  filters: MessageFilters[];
  onEdit(index: number, filter: MessageFilters): void;
  deleteFilter(index: number): void;
  activeFilterHandler(activeFilter: MessageFilters, index: number): void;
  closeModal(): void;
  onGoBack(): void;
  activeFilter?: MessageFilters;
}

const SavedFilters: FC<Props> = ({
  filters,
  onEdit,
  deleteFilter,
  activeFilterHandler,
  closeModal,
  onGoBack,
  activeFilter,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const [selectedFilter, setSelectedFilter] = React.useState(-1);
  const confirm = useConfirm();

  const activateFilter = () => {
    if (selectedFilter > -1) {
      activeFilterHandler(filters[selectedFilter], selectedFilter);
    }
    closeModal();
  };

  const deleteFilterHandler = (index: number) => {
    const filterName = filters[index]?.name;
    const isFilterSelected = activeFilter && activeFilter.name === filterName;

    // 确认删除过滤器的提示，若该过滤器当前被选中则附带警告
    const confirmMessage = isFilterSelected
      ? `${t('topic.areYouSureRemoveFilter', { filterName })}\n${t(
          'topic.warningFilterSelected'
        )}`
      : t('topic.areYouSureRemoveFilter', { filterName });

    confirm(confirmMessage, () => {
      deleteFilter(index);
      setSelectedFilter(-1);
    });
  };

  return (
    <>
      <S.BackToCustomText onClick={onGoBack}>
        {t('topic.backToCreateFilters')}
      </S.BackToCustomText>
      <S.SavedFiltersContainer>
        <S.CreatedFilter>{t('topic.savedFilters')}</S.CreatedFilter>
        {filters.length === 0 && (
          <S.NoSavedFilter>{t('topic.noSavedFilters')}</S.NoSavedFilter>
        )}
        {filters.map((filter, index) => (
          <S.SavedFilter
            key={Symbol(filter.name).toString()}
            selected={selectedFilter === index}
            onClick={() => setSelectedFilter(index)}
          >
            <S.SavedFilterName>{filter.name}</S.SavedFilterName>
            <S.FilterOptions>
              <S.FilterEdit onClick={() => onEdit(index, filter)}>
                {t('common.edit')}
              </S.FilterEdit>
              <S.DeleteSavedFilter onClick={() => deleteFilterHandler(index)}>
                <DeleteIcon />
              </S.DeleteSavedFilter>
            </S.FilterOptions>
          </S.SavedFilter>
        ))}
      </S.SavedFiltersContainer>
      <S.FilterButtonWrapper>
        <Button
          buttonSize="M"
          buttonType="secondary"
          type="button"
          onClick={closeModal}
        >
          {t('common.cancel')}
        </Button>
        <Button
          buttonSize="M"
          buttonType="primary"
          type="button"
          onClick={activateFilter}
          disabled={selectedFilter === -1}
        >
          {t('topic.selectFilter')}
        </Button>
      </S.FilterButtonWrapper>
    </>
  );
};

export default SavedFilters;