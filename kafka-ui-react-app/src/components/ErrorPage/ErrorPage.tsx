import React from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from 'components/common/Button/Button';

import * as S from './ErrorPage.styled';

interface Props {
  status?: number;
  text?: string;
  btnText?: string;
}

const ErrorPage: React.FC<Props> = ({
  status = 404,
  text,
  btnText,
}) => {
  const { t } = useTranslation();
  // 使用 i18n 翻译错误页面文本，如果外部传入了 props 则优先使用 props
  const displayText = text || t('error.notFound');
  const displayBtnText = btnText || t('common.goBackToDashboard');

  return (
    <S.Wrapper>
      <S.Status>{status}</S.Status>
      <S.Text>{displayText}</S.Text>
      <Button buttonType="primary" buttonSize="M" to="/">
        {displayBtnText}
      </Button>
    </S.Wrapper>
  );
};

export default ErrorPage;
