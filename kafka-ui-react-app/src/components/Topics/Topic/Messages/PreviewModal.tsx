import React, { useEffect } from 'react';
import { Button } from 'components/common/Button/Button';
import { FormError } from 'components/common/Input/Input.styled';
import Input from 'components/common/Input/Input';
import { InputLabel } from 'components/common/Input/InputLabel.styled';
import IconButtonWrapper from 'components/common/Icons/IconButtonWrapper';
import EditIcon from 'components/common/Icons/EditIcon';
import CancelIcon from 'components/common/Icons/CancelIcon';

import * as S from './PreviewModal.styled';
import { useTranslation } from 'react-i18next';
import { PreviewFilter } from './Message';

export interface InfoModalProps {
  values: PreviewFilter[];
  toggleIsOpen(): void;
  setFilters: (payload: PreviewFilter[]) => void;
}

const PreviewModal: React.FC<InfoModalProps> = ({
  values,
  toggleIsOpen,
  setFilters,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const [field, setField] = React.useState('');
  const [path, setPath] = React.useState('');
  const [errors, setErrors] = React.useState<string[]>([]);
  const [editIndex, setEditIndex] = React.useState<number | undefined>();

  const handleOk = () => {
    const newErrors = [];

    if (field === '') {
      newErrors.push('field');
    }

    if (path === '') {
      newErrors.push('path');
    }

    if (newErrors?.length) {
      setErrors(newErrors);
      return;
    }

    const newValues = [...values];

    if (typeof editIndex !== 'undefined') {
      newValues.splice(editIndex, 1, { field, path });
    } else {
      newValues.push({ field, path });
    }

    setFilters(newValues);
    toggleIsOpen();
  };

  const handleRemove = (filter: PreviewFilter) => {
    const newValues = values.filter(
      (item) => item.field !== filter.field && item.path !== filter.path
    );

    setFilters(newValues);
  };

  useEffect(() => {
    if (values?.length && typeof editIndex !== 'undefined') {
      setField(values[editIndex].field);
      setPath(values[editIndex].path);
    }
  }, [editIndex]);

  return (
    <S.PreviewModal>
      {values.map((item, index) => (
        <S.EditForm key="index">
          <S.Field>
            {' '}
            {item.field} : {item.path}
          </S.Field>
          <IconButtonWrapper role="button" onClick={() => setEditIndex(index)}>
            <EditIcon />
          </IconButtonWrapper>
          {'  '}
          <IconButtonWrapper role="button" onClick={() => handleRemove(item)}>
            <CancelIcon />
          </IconButtonWrapper>
        </S.EditForm>
      ))}
      <div>
        <InputLabel htmlFor="previewFormField">{t('topic.field')}</InputLabel>
        <Input
          type="text"
          id="previewFormField"
          min="1"
          value={field}
          placeholder={t('topic.field')}
          onChange={({ target }) => setField(target?.value)}
        />
        <FormError>{errors.includes('field') && t('topic.fieldRequired')}</FormError>
      </div>
      <div>
        <InputLabel htmlFor="previewFormJsonPath">{t('topic.jsonPath')}</InputLabel>
        <Input
          type="text"
          id="previewFormJsonPath"
          min="1"
          value={path}
          placeholder={t('topic.jsonPath')}
          onChange={({ target }) => setPath(target?.value)}
        />
        <FormError>
          {errors.includes('path') && t('topic.jsonPathRequired')}
        </FormError>
      </div>
      <S.ButtonWrapper>
        <Button
          buttonSize="M"
          buttonType="secondary"
          type="button"
          onClick={toggleIsOpen}
        >
          {t('common.close')}
        </Button>
        <Button
          buttonSize="M"
          buttonType="secondary"
          type="button"
          onClick={handleOk}
        >
          {t('common.save')}
        </Button>
      </S.ButtonWrapper>
    </S.PreviewModal>
  );
};

export default PreviewModal;