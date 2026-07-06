import React from 'react';
import * as S from 'components/Topics/Topic/Messages/Filters/Filters.styled';
import { useTranslation } from 'react-i18next';
import { Button } from 'components/common/Button/Button';

interface InfoModalProps {
  toggleIsOpen(): void;
}

const InfoModal: React.FC<InfoModalProps> = ({ toggleIsOpen }) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  return (
    <S.InfoModal>
      <S.InfoParagraph>
        <b>{t('topic.variablesBound')}</b> partition, timestampMs,
        keyAsText, valueAsText, header, key (json if possible), value (json if
        possible).
      </S.InfoParagraph>
      <S.InfoParagraph>
        <b>{t('topic.infoModal.jsonParsingLogic')}</b>
      </S.InfoParagraph>
      <S.InfoParagraph>{t('topic.infoModal.jsonParsingDesc')}</S.InfoParagraph>
      <S.InfoParagraph>
        <b>{t('topic.sampleFilters')}</b>
      </S.InfoParagraph>
      <ol aria-label={t('topic.infoModal.jsonParsingLogic')}>
        <S.ListItem>
          <code>keyAsText != null && keyAsText ~&quot;([Gg])roovy&quot;</code> -{' '}
          {t('topic.infoModal.regexKeyString')}
        </S.ListItem>
        <S.ListItem>
          <code>
            value.name == &quot;iS.ListItemax&quot; && value.age &gt; 30
          </code>{' '}
          - {t('topic.infoModal.caseValueIsJson')}
        </S.ListItem>
        <S.ListItem>
          <code>value == null && valueAsText != null</code> -{' '}
          {t('topic.infoModal.searchNonNullOrNonJson')}
        </S.ListItem>
        <S.ListItem>
          <code>
            headers.sentBy == &quot;some system&quot; &&
            headers[&quot;sentAt&quot;] == &quot;2020-01-01&quot;
          </code>
        </S.ListItem>
        <S.ListItem>
          {t('topic.infoModal.multilineFilters')}
          <S.InfoParagraph>
            <pre>
              def name = value.name
              <br />
              def age = value.age
              <br />
              name == &quot;iliax&quot; && age == 30
              <br />
            </pre>
          </S.InfoParagraph>
        </S.ListItem>
      </ol>
      <S.ButtonContainer>
        <Button
          buttonSize="M"
          buttonType="secondary"
          type="button"
          onClick={toggleIsOpen}
        >
          {t('common.ok')}
        </Button>
      </S.ButtonContainer>
    </S.InfoModal>
  );
};

export default InfoModal;