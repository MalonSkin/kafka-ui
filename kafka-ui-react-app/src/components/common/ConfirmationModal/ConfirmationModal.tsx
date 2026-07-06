import React from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from 'components/common/Button/Button';
import { ConfirmContext } from 'components/contexts/ConfirmContext';

import * as S from './ConfirmationModal.styled';

const ConfirmationModal: React.FC = () => {
  const { t } = useTranslation();
  const context = React.useContext(ConfirmContext);
  const isOpen = context?.content && context?.confirm;

  if (!isOpen) return null;

  return (
    <S.Wrapper role="dialog" aria-label={t('confirmation.confirmAction')}>
      <S.Overlay onClick={context.cancel} aria-hidden="true" role="button" />
      <S.Modal>
        <S.Header>{t('confirmation.confirmAction')}</S.Header>
        <S.Content>{context.content}</S.Content>
        <S.Footer>
          <Button
            buttonType="secondary"
            buttonSize="M"
            onClick={context.cancel}
            type="button"
          >
            {t('common.cancel')}
          </Button>
          <Button
            buttonType={context.dangerButton ? 'danger' : 'primary'}
            buttonSize="M"
            onClick={context.confirm}
            type="button"
          >
            {t('common.confirm')}
          </Button>
        </S.Footer>
      </S.Modal>
    </S.Wrapper>
  );
};

export default ConfirmationModal;
