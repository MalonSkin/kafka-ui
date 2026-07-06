import React from 'react';
import { useTranslation } from 'react-i18next';
import { SchemaSubject } from 'generated-sources';
import EditorViewer from 'components/common/EditorViewer/EditorViewer';
import Heading from 'components/common/heading/Heading.styled';

import * as S from './LatestVersionItem.styled';

interface LatestVersionProps {
  schema: SchemaSubject;
}

const LatestVersionItem: React.FC<LatestVersionProps> = ({
  schema: { id, subject, schema, compatibilityLevel, version, schemaType },
}) => {
  const { t } = useTranslation();
  return (
  <S.Wrapper>
    <div>
      <Heading level={3}>{t('schema.actualVersion')}</Heading>
      <EditorViewer data={schema} schemaType={schemaType} maxLines={28} />
    </div>
    <div>
      <div>
        <S.MetaDataLabel>{t('schema.latestVersion')}</S.MetaDataLabel>
        <p>{version}</p>
      </div>
      <div>
        <S.MetaDataLabel>{t('common.id')}</S.MetaDataLabel>
        <p>{id}</p>
      </div>
      <div>
        <S.MetaDataLabel>{t('common.type')}</S.MetaDataLabel>
        <p>{schemaType}</p>
      </div>
      <div>
        <S.MetaDataLabel>{t('schema.subject')}</S.MetaDataLabel>
        <p>{subject}</p>
      </div>
      <div>
        <S.MetaDataLabel>{t('schema.compatibility')}</S.MetaDataLabel>
        <p>{compatibilityLevel}</p>
      </div>
    </div>
  </S.Wrapper>
  );
};

export default LatestVersionItem;
