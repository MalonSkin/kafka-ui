import React from 'react';
import EditorViewer from 'components/common/EditorViewer/EditorViewer';
import BytesFormatted from 'components/common/BytesFormatted/BytesFormatted';
import { SchemaType, TopicMessageTimestampTypeEnum } from 'generated-sources';
import { formatTimestamp } from 'lib/dateTimeHelpers';

import * as S from './MessageContent.styled';
import { useTranslation } from 'react-i18next';

type Tab = 'key' | 'content' | 'headers';

export interface MessageContentProps {
  messageKey?: string;
  messageContent?: string;
  headers?: { [key: string]: string | undefined };
  timestamp?: Date;
  timestampType?: TopicMessageTimestampTypeEnum;
  keySize?: number;
  contentSize?: number;
  keySerde?: string;
  valueSerde?: string;
}

const MessageContent: React.FC<MessageContentProps> = ({
  messageKey,
  messageContent,
  headers,
  timestamp,
  timestampType,
  keySize,
  contentSize,
  keySerde,
  valueSerde,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = React.useState<Tab>('content');
  const activeTabContent = () => {
    switch (activeTab) {
      case 'content':
        return messageContent;
      case 'key':
        return messageKey;
      default:
        return JSON.stringify(headers);
    }
  };

  const handleKeyTabClick = (e: React.MouseEvent) => {
    e.preventDefault();
    setActiveTab('key');
  };

  const handleContentTabClick = (e: React.MouseEvent) => {
    e.preventDefault();
    setActiveTab('content');
  };

  const handleHeadersTabClick = (e: React.MouseEvent) => {
    e.preventDefault();
    setActiveTab('headers');
  };

  const contentType =
    messageContent && messageContent.trim().startsWith('{')
      ? SchemaType.JSON
      : SchemaType.PROTOBUF;

  return (
    <S.Wrapper>
      <td colSpan={10}>
        <S.Section>
          <S.ContentBox>
            <S.Tabs>
              <S.Tab
                type="button"
                $active={activeTab === 'key'}
                onClick={handleKeyTabClick}
              >
                {t('topic.key')}
              </S.Tab>
              <S.Tab
                $active={activeTab === 'content'}
                type="button"
                onClick={handleContentTabClick}
              >
                {t('common.value')}
              </S.Tab>
              <S.Tab
                $active={activeTab === 'headers'}
                type="button"
                onClick={handleHeadersTabClick}
              >
                {t('topic.headers')}
              </S.Tab>
            </S.Tabs>
            <EditorViewer
              data={activeTabContent() || ''}
              maxLines={28}
              schemaType={contentType}
            />
          </S.ContentBox>
          <S.MetadataWrapper>
            <S.Metadata>
              <S.MetadataLabel>{t('topic.timestamp')}</S.MetadataLabel>
              <span>
                <S.MetadataValue>{formatTimestamp(timestamp)}</S.MetadataValue>
                {/* 时间戳类型，复用 timestamp 翻译并拼接 type 后缀 */}
                <S.MetadataMeta>{t('topic.timestamp')} type: {timestampType}</S.MetadataMeta>
              </span>
            </S.Metadata>

            <S.Metadata>
              <S.MetadataLabel>{t('topic.keySerde')}</S.MetadataLabel>
              <span>
                <S.MetadataValue>{keySerde}</S.MetadataValue>
                <S.MetadataMeta>
                  {t('topic.sizeLabel')} <BytesFormatted value={keySize} />
                </S.MetadataMeta>
              </span>
            </S.Metadata>

            <S.Metadata>
              <S.MetadataLabel>{t('topic.valueSerde')}</S.MetadataLabel>
              <span>
                <S.MetadataValue>{valueSerde}</S.MetadataValue>
                <S.MetadataMeta>
                  {t('topic.sizeLabel')} <BytesFormatted value={contentSize} />
                </S.MetadataMeta>
              </span>
            </S.Metadata>
          </S.MetadataWrapper>
        </S.Section>
      </td>
    </S.Wrapper>
  );
};

export default MessageContent;